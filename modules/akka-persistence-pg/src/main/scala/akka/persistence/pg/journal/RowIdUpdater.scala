package akka.persistence.pg.journal

import akka.pattern.pipe
import akka.actor.{Stash, Props, ActorLogging, Actor}
import akka.persistence.pg.{PluginConfig, PgConfig}
import akka.persistence.pg.journal.RowIdUpdater.UpdateRowIds

import scala.concurrent.Future
import scala.util.control.NonFatal

object RowIdUpdater {

  case object UpdateRowIds

  def props(pluginConfig: PluginConfig) = Props(new RowIdUpdater(pluginConfig))

}

class RowIdUpdater(override val pluginConfig: PluginConfig) extends Actor
  with Stash
  with ActorLogging
  with PgConfig {

  import context.dispatcher

  private case object Marker
  private case object Done

  def waitingForUpdateRequest: Receive = {
    case UpdateRowIds =>
      self ! Marker
      context become ignoreUntilMarker
  }

  def ignoreUntilMarker: Receive = {
    case UpdateRowIds =>
    case Marker       =>
      assignRowIds() map {_ => Done} recover {
        case NonFatal(t) => log.error(t, "could not update rowids"); Done
      } pipeTo self
      context become updateRunning
  }

  def updateRunning: Receive = {
    case Done =>
      unstashAll()
      context.become(waitingForUpdateRequest)
    case UpdateRowIds => stash()
  }

  override def receive: Receive = waitingForUpdateRequest

  import driver.api._


  // UPDATE WITH ORDER BY see http://stackoverflow.com/questions/16735950/update-with-order-by
  def assignRowIds(): Future[Unit] = {
    database.run(
      sqlu"""update #${pluginConfig.fullJournalTableName} as j set rowid = nextval('#${pluginConfig.fullRowIdSequenceName}') FROM (select id FROM #${pluginConfig.fullJournalTableName} order by id) as q where q.id = j.id AND j.rowid is NULL""")
      .map { c => log.debug("updated rowid for {} rows", c); }
  }


}
