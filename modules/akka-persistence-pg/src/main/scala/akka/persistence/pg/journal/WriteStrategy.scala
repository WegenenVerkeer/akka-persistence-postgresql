package akka.persistence.pg.journal


import java.util.concurrent.TimeUnit

import akka.actor.{Status, ActorSystem, ActorRef}
import akka.pattern.ask
import akka.persistence.pg.journal.StoreActor.{StoreSuccess, Store}
import akka.persistence.pg.{PluginConfig, PgConfig}
import akka.util.Timeout

import scala.concurrent.Future

trait WriteStrategy {
  self: PgConfig =>

  import driver.api._

  def store(actions: Seq[DBIO[_]]): Future[Unit]
  def system: ActorSystem

}

class NonTransactionalWriteStrategy(override val pluginConfig: PluginConfig,
                                    override val system: ActorSystem) extends WriteStrategy
with PgConfig {

  import driver.api._

  def store(actions: Seq[DBIO[_]]): Future[Unit] = {
    database.run { DBIO.seq(actions:_*) }
  }
}

class SingleThreadedBatchWriteStrategy(override val pluginConfig: PluginConfig,
                                       override val system: ActorSystem) extends WriteStrategy
  with PgConfig {

  import driver.api._
  implicit val timeout = Timeout(10, TimeUnit.SECONDS)
  import scala.concurrent.ExecutionContext.Implicits.global

  private val eventStoreActor: ActorRef = system.actorOf(StoreActor.props(pluginConfig))

  def store(actions: Seq[DBIO[_]]): Future[Unit] = {
    eventStoreActor ? Store(actions) flatMap {
      case StoreSuccess      => Future.successful(())
      case Status.Failure(t) => Future.failed(t)
    }
  }


}

class TransactionalWriteStrategy(override val pluginConfig: PluginConfig,
                                 override val system: ActorSystem) extends WriteStrategy
  with PgConfig {

  import driver.api._

  def store(actions: Seq[DBIO[_]]): Future[Unit] = {
    database.run {
      DBIO.seq(actions:_*).transactionally
    }
  }
}

class TableLockingWriteStrategy(override val pluginConfig: PluginConfig,
                                override val system: ActorSystem) extends WriteStrategy
  with PgConfig {

  import driver.api._

  def store(actions: Seq[DBIO[_]]): Future[Unit] = {
    database.run {
      DBIO.seq((sqlu"""lock table #${pluginConfig.fullJournalTableName} in share update exclusive mode"""
        +: actions):_*).transactionally
    }
  }

}

class RowIdUpdatingStrategy(override val pluginConfig: PluginConfig,
                            override val system: ActorSystem) extends WriteStrategy
  with PgConfig {

  import driver.api._
  import system.dispatcher

  private val rowIdUpdater: ActorRef = system.actorOf(RowIdUpdater.props(pluginConfig))

  def store(actions: Seq[DBIO[_]]): Future[Unit] = {
    val r = database.run(DBIO.seq(actions:_*).transactionally)
    r.onSuccess { case _ =>
      rowIdUpdater ! RowIdUpdater.UpdateRowIds
    }
    r
  }

}

