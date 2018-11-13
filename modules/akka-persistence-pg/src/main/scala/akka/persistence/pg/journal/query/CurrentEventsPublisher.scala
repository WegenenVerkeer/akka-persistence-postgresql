package akka.persistence.pg.journal.query

import akka.actor.Props
import akka.persistence.JournalProtocol.RecoverySuccess

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

  extends LiveEventsPublisher(fromOffset, toOffset, refreshInterval, maxBufSize, writeJournalPluginId) {


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

