package akka.persistence.pg.journal


import java.util.concurrent.TimeUnit

import akka.actor.{Status, ActorSystem, ActorRef}
import akka.pattern.ask
import akka.persistence.pg.journal.StoreActor.{StoreSuccess, Store}
import akka.persistence.pg.PluginConfig
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}


trait WriteStrategy {

  def pluginConfig: PluginConfig
  lazy val driver = pluginConfig.pgPostgresDriver

  import driver.api._

  trait DbLike {
    def run[R](a: DBIOAction[R, NoStream, Nothing])
              (implicit executionContext: ExecutionContext): Future[R]
  }

  def store(actions: Seq[DBIO[_]])
           (implicit executionContext: ExecutionContext): Future[Unit]
  def system: ActorSystem

  val throttler = if (pluginConfig.throttled) {
    new ConcurrentMessagesThrottlerImpl(pluginConfig.dbConfig.getInt("numThreads"), system, pluginConfig.throttleTimeout)
  } else {
    NotThrottled
  }

  lazy val database = {
    new DbLike {
      override def run[R](a: DBIOAction[R, NoStream, Nothing])
                         (implicit executionContext: ExecutionContext): Future[R] = {
        throttler.throttled {
          pluginConfig.database.run(a)
        }
      }
    }
  }

}

class SingleThreadedBatchWriteStrategy(override val pluginConfig: PluginConfig,
                                       override val system: ActorSystem) extends WriteStrategy {

  import driver.api._
  implicit val timeout = Timeout(10, TimeUnit.SECONDS)

  private val eventStoreActor: ActorRef = system.actorOf(StoreActor.props(pluginConfig.pgPostgresDriver, database))

  def store(actions: Seq[DBIO[_]])
           (implicit executionContext: ExecutionContext): Future[Unit] = {
    eventStoreActor ? Store(actions) flatMap {
      case StoreSuccess      => Future.successful(())
      case Status.Failure(t) => Future.failed(t)
    }
  }


}

class TransactionalWriteStrategy(override val pluginConfig: PluginConfig,
                                 override val system: ActorSystem) extends WriteStrategy {

  import pluginConfig.pgPostgresDriver.api._

  def store(actions: Seq[DBIO[_]])
           (implicit executionContext: ExecutionContext): Future[Unit] = {
    database.run {
      DBIO.seq(actions:_*).transactionally
    }
  }
}

class TableLockingWriteStrategy(override val pluginConfig: PluginConfig,
                                override val system: ActorSystem) extends WriteStrategy {

  import pluginConfig.pgPostgresDriver.api._

  def store(actions: Seq[DBIO[_]])
           (implicit executionContext: ExecutionContext): Future[Unit] = {
    database.run {
      DBIO.seq((sqlu"""lock table #${pluginConfig.fullJournalTableName} in share update exclusive mode"""
        +: actions):_*).transactionally
    }
  }

}

class RowIdUpdatingStrategy(override val pluginConfig: PluginConfig,
                            override val system: ActorSystem) extends WriteStrategy {

  import driver.api._
  
  private val rowIdUpdater: ActorRef = system.actorOf(RowIdUpdater.props(pluginConfig))

  def store(actions: Seq[DBIO[_]])
           (implicit executionContext: ExecutionContext): Future[Unit] = {
    val r = database.run(DBIO.seq(actions:_*).transactionally)
    r.onSuccess { case _ =>
      rowIdUpdater ! RowIdUpdater.UpdateRowIds
    }
    r
  }

}

