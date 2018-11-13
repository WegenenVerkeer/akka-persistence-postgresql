package akka.persistence.pg.journal.query

import akka.actor.Props

import scala.concurrent.duration.FiniteDuration

private[akka] object CurrentEventsByPersistenceIdPublisher {

  def props(persistenceId: String,
            fromOffset: Long,
            toOffset: Long,
            refreshInterval: FiniteDuration,
            maxBufSize: Int,
            writeJournalPluginId: String): Props = {

    Props(new CurrentEventsByPersistenceIdPublisher(persistenceId, fromOffset, toOffset, refreshInterval, maxBufSize, writeJournalPluginId))
  }
}

class CurrentEventsByPersistenceIdPublisher(persistenceId: String,
                                            fromOffset: Long,
                                            toOffset: Long,
                                            refreshInterval: FiniteDuration,
                                            maxBufSize: Int,
                                            writeJournalPluginId: String)
  extends LiveEventsByPersistenceIdPublisher(persistenceId, fromOffset, toOffset, refreshInterval, maxBufSize, writeJournalPluginId)
    with CurrentEventsQueries {

  override def subscribe(): Unit = ()

  override def replaying: Receive = currentEventsBehavior orElse super.replaying

}

