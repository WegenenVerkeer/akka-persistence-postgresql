package akka.persistence.pg.journal.query

import akka.actor.Props
import akka.persistence.JournalProtocol.RecoverySuccess
import akka.persistence.pg.EventTag

import scala.concurrent.duration.FiniteDuration

private[akka] object CurrentEventsByTagsPublisher {

  def props(tags: Set[EventTag],
            fromOffset: Long,
            toOffset: Long,
            refreshInterval: FiniteDuration,
            maxBufSize: Int,
            writeJournalPluginId: String): Props = {

    Props(new CurrentEventsByTagsPublisher(tags, fromOffset, toOffset, refreshInterval, maxBufSize, writeJournalPluginId))
  }
}

class CurrentEventsByTagsPublisher(tags: Set[EventTag],
                                   fromOffset: Long,
                                   toOffset: Long,
                                   refreshInterval: FiniteDuration,
                                   maxBufSize: Int,
                                   writeJournalPluginId: String)
  extends LiveEventsByTagsPublisher(tags, fromOffset, toOffset, refreshInterval, maxBufSize, writeJournalPluginId) {

  override def subscribe(): Unit = ()

  override def replaying: Receive = {
    val receive: Receive = {
      case RecoverySuccess(_) =>
        deliverBuf(Long.MaxValue)
        onCompleteThenStop()
    }

    receive orElse super.replaying
  }

}

