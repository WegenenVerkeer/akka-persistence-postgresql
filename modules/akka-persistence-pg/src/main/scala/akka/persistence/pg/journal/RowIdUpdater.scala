package akka.persistence.pg.journal

import akka.actor._
import akka.pattern.pipe
import akka.persistence.pg.journal.RowIdUpdater.{IsBusy, UpdateRowIds}
import akka.persistence.pg.PluginConfig

import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.util.control.NonFatal

object RowIdUpdater {

  case class UpdateRowIds(notifier: Notifier)
  case object IsBusy

  def props(pluginConfig: PluginConfig) = Props(new RowIdUpdater(pluginConfig))

}

class RowIdUpdater(pluginConfig: PluginConfig) extends Actor with Stash with ActorLogging {

  import context.dispatcher

  private case object Init
  private case object Marker
  private case object Done
  private case object Continue
  private case class MaxRowId(rowid: Long)

  //TODO make configurable
  val max = 20000

  var maxRowId: Long             = _
  var notifiers: Queue[Notifier] = Queue.empty

  //start initializing => find max rowid
  self ! Init

  override def receive: Receive = initializing

  def initializing: Receive = {
    case IsBusy          => sender ! true
    case UpdateRowIds(_) => stash()
    case Init =>
      findMaxRowId() map { MaxRowId } pipeTo self
      ()
    case MaxRowId(rowid) =>
      maxRowId = rowid
      unstashAll()
      context become waitingForUpdateRequest
  }

  def waitingForUpdateRequest: Receive = {
    case UpdateRowIds(notifier) =>
      notifiers = notifiers.enqueue(notifier)
      self ! Marker
      context become ignoreUntilMarker
    case IsBusy => sender ! false
  }

  def ignoreUntilMarker: Receive = {
    case IsBusy => sender ! true
    case UpdateRowIds(notifier) =>
      notifiers = notifiers.enqueue(notifier)
    case Marker =>
      assignRowIds() map { updated =>
        if (updated == max) Continue else Done
      } recover {
        case NonFatal(t) => log.error(t, "could not update rowids"); Done
      } pipeTo self
      context become updateRunning
  }

  def updateRunning: Receive = {
    case IsBusy => sender ! true
    case Done =>
      unstashAll()
      notifyEventsAvailable()
      context become waitingForUpdateRequest
    case Continue =>
      unstashAll()
      notifyEventsAvailable()
      self ! Marker
      context become ignoreUntilMarker
    case UpdateRowIds(_) =>
      stash()
  }

  def notifyEventsAvailable(): Unit = {
    notifiers.foreach { _.eventsAvailable() }
    notifiers = Queue.empty
  }

  import pluginConfig.pgPostgresProfile.api._

  def findMaxRowId(): Future[Long] =
    pluginConfig.database
      .run(sql"""SELECT COALESCE(MAX(rowid), 0::bigint) FROM #${pluginConfig.fullJournalTableName}""".as[Long])
      .map(_(0))

  def assignRowIds(): Future[Int] = {
    var updated = 0
    pluginConfig.database
      .run(
        sql"""SELECT id FROM #${pluginConfig.fullJournalTableName} WHERE rowid IS NULL ORDER BY id limit #$max"""
          .as[Long]
          .flatMap { ids =>
            updated += ids.size
            if (updated > 0) {
              val values = ids
                .map { id =>
                  maxRowId += 1
                  s"($id, $maxRowId)"
                }
                .mkString(",")
              sqlu"""UPDATE #${pluginConfig.fullJournalTableName} SET rowid = data_table.rowid
                  FROM (VALUES #$values) as data_table (id, rowid)
                  WHERE #${pluginConfig.fullJournalTableName}.id = data_table.id"""
            } else {
              DBIO.successful(())
            }
          }
      )
      .map { _ =>
        log.debug("updated rowid for {} rows", updated)
        updated
      }
  }

}
