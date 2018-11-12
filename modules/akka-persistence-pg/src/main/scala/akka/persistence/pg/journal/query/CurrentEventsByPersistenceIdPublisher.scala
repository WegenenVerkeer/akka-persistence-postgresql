package akka.persistence.pg.journal.query

import akka.actor.Props
import akka.persistence.JournalProtocol.RecoverySuccess

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
  extends LiveEventsByPersistenceIdPublisher(persistenceId, fromOffset, toOffset, refreshInterval, maxBufSize, writeJournalPluginId) {

  override def replaying: Receive = {
    val receive: Receive = {
      case RecoverySuccess(_) =>
        deliverBuf()
        onCompleteThenStop()
    }

    receive orElse super.replaying
  }

}

