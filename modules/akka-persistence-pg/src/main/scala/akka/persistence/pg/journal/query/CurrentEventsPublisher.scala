package akka.persistence.pg.journal.query

import akka.actor.Props

import scala.concurrent.duration.FiniteDuration

private[akka] object CurrentEventsPublisher {

  def props(fromOffset: Long,
            toOffset: Long,
            refreshInterval: FiniteDuration,
            maxBufSize: Int,
            writeJournalPluginId: String): Props = {

    Props(new CurrentEventsPublisher(fromOffset, toOffset, refreshInterval, maxBufSize, writeJournalPluginId))
  }
}

class CurrentEventsPublisher(fromOffset: Long,
                             toOffset: Long,
                             refreshInterval: FiniteDuration,
                             maxBufSize: Int,
                             writeJournalPluginId: String)

  extends LiveEventsPublisher(fromOffset, toOffset, refreshInterval, maxBufSize, writeJournalPluginId) with CurrentEventsQueries {

  override def subscribe(): Unit = ()

  override def replaying: Receive = currentEventsBehavior orElse super.replaying

}

