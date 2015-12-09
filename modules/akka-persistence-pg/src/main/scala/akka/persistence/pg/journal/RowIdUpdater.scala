package akka.persistence.pg.journal

import akka.actor._
import akka.pattern.pipe
import akka.persistence.pg.journal.RowIdUpdater.{IsBusy, UpdateRowIds}
import akka.persistence.pg.{PgPostgresDriver, PluginConfig}

import scala.concurrent.Future
import scala.util.control.NonFatal

object RowIdUpdater {

  case object UpdateRowIds
  case object IsBusy

  def props(pluginConfig: PluginConfig,
            driver: PgPostgresDriver,
            database: WriteStrategy#DbLike) = Props(new RowIdUpdater(pluginConfig, driver, database))

}

class RowIdUpdater(pluginConfig: PluginConfig,
                   driver: PgPostgresDriver,
                   database: WriteStrategy#DbLike) extends Actor
  with Stash
  with ActorLogging {

  import context.dispatcher

  private case object Marker
  private case object Done
  private case object Continue

  //TODO make configurable
  val max = 20000

  def waitingForUpdateRequest: Receive = {
    case UpdateRowIds =>
      self ! Marker
      context become ignoreUntilMarker
    case IsBusy => sender ! false
  }

  def ignoreUntilMarker: Receive = {
    case IsBusy => sender ! true
    case UpdateRowIds =>
    case Marker       =>
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
      context become waitingForUpdateRequest
    case Continue =>
      unstashAll()
      self ! Marker
      context become ignoreUntilMarker
    case UpdateRowIds =>
      stash()
  }

  override def receive: Receive = waitingForUpdateRequest

  import driver.api._

  def assignRowIds(): Future[Int] = {
    var updated = 0
    database.run(
      sql"""SELECT id FROM #${pluginConfig.fullJournalTableName} WHERE rowid IS NULL ORDER BY id limit #$max""".as[Long]
        .flatMap { ids =>
          updated += ids.size
          if (updated > 0) {
            sql"""SELECT nextval('#${pluginConfig.fullRowIdSequenceName}') from generate_series(1, $updated)""".as[Long]
              .flatMap { rowids =>
                val values = ids.zip(rowids).map { case (id, rowid) => s"($id, $rowid)" }.mkString(",")
                sqlu"""UPDATE #${pluginConfig.fullJournalTableName} SET rowid = data_table.rowid
                    FROM (VALUES #$values) as data_table (id, rowid)
                    WHERE #${pluginConfig.fullJournalTableName}.id = data_table.id"""
              }
          } else {
            DBIO.successful(())
          }
        })
      .map { _ =>
        log.debug("updated rowid for {} rows", updated)
        updated
      }
  }

//  def assignRowIds2(): Future[Int] = {
//    database.run(
//      sqlu"""UPDATE #${pluginConfig.fullJournalTableName} SET rowid = newid
//             FROM (SELECT id, nextval('#${pluginConfig.fullRowIdSequenceName}') as newid
//                     FROM (SELECT id FROM #${pluginConfig.fullJournalTableName} WHERE rowid IS NULL ORDER BY id) AS pre_empty_ids ) as empty_ids
//               WHERE
//                #${pluginConfig.fullJournalTableName}.id = empty_ids.id
//                AND #${pluginConfig.fullJournalTableName}.rowid IS NULL"""
//
//    ).map {
//      c => log.debug("updated rowid for {} rows", c);
//      c
//    }
//  }


}
