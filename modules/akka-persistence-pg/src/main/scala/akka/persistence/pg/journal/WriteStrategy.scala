package akka.persistence.pg.journal

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, Status}
import akka.persistence.pg.PluginConfig
import akka.persistence.pg.journal.StoreActor.{Store, StoreSuccess}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

trait WriteStrategy {

  def pluginConfig: PluginConfig
  lazy val driver = pluginConfig.pgPostgresProfile

  import driver.api._

  def store(actions: Seq[DBIO[_]], notifier: Notifier)(implicit executionContext: ExecutionContext): Future[Unit]
  def system: ActorSystem

}

class SingleThreadedBatchWriteStrategy(override val pluginConfig: PluginConfig, override val system: ActorSystem)
    extends WriteStrategy {

  import driver.api._
  implicit val timeout = Timeout(10, TimeUnit.SECONDS)

  private val eventStoreActor: ActorRef = system.actorOf(StoreActor.props(pluginConfig))

  override def store(actions: Seq[DBIO[_]], notifier: Notifier)(
      implicit executionContext: ExecutionContext
  ): Future[Unit] =
    eventStoreActor ? Store(actions) flatMap {
      case StoreSuccess      => Future.successful(())
      case Status.Failure(t) => Future.failed(t)
    } map { _ =>
      notifier.eventsAvailable()
    }

}

/**
  * This writestrategy can lead to missing events, only usefull as a benchmarking baseline
  *
  * @param pluginConfig
  * @param system
  */
class TransactionalWriteStrategy(override val pluginConfig: PluginConfig, override val system: ActorSystem)
    extends WriteStrategy {

  system.log.warning(
    """
      |!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
      |!                                                                                                          !
      |!  TransactionalWriteStrategy is configured:                                                               !
      |!                                                                                                          !
      |!  A possible, but likely consequence is that while reading events, some events might be missed            !
      |!  This strategy is only useful for benchmarking!                                                          !
      |!  Use with caution, YOLO !!!                                                                              !
      |!                                                                                                          !
      |!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    """.stripMargin
  )

  import pluginConfig.pgPostgresProfile.api._

  def store(actions: Seq[DBIO[_]], notifier: Notifier)(implicit executionContext: ExecutionContext): Future[Unit] =
    pluginConfig.database
      .run {
        DBIO.seq(actions: _*).transactionally
      }
      .map { _ =>
        notifier.eventsAvailable()
      }
}

class TableLockingWriteStrategy(override val pluginConfig: PluginConfig, override val system: ActorSystem)
    extends WriteStrategy {

  import pluginConfig.pgPostgresProfile.api._

  def store(actions: Seq[DBIO[_]], notifier: Notifier)(implicit executionContext: ExecutionContext): Future[Unit] =
    pluginConfig.database
      .run {
        DBIO
          .seq(
            (sqlu"""lock table #${pluginConfig.fullJournalTableName} in share row exclusive mode"""
              +: actions): _*
          )
          .transactionally
      }
      .map { _ =>
        notifier.eventsAvailable()
      }

}

class RowIdUpdatingStrategy(override val pluginConfig: PluginConfig, override val system: ActorSystem)
    extends WriteStrategy {

  import driver.api._

  private val rowIdUpdater: ActorRef = system.actorOf(RowIdUpdater.props(pluginConfig), "AkkaPgRowIdUpdater")

  def store(actions: Seq[DBIO[_]], notifier: Notifier)(implicit executionContext: ExecutionContext): Future[Unit] =
    pluginConfig.database
      .run(DBIO.seq(actions: _*).transactionally)
      .map { _ =>
        rowIdUpdater ! RowIdUpdater.UpdateRowIds(notifier)
      }

}
