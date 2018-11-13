package akka.persistence.pg.journal.query

import akka.persistence.JournalProtocol.RecoverySuccess
import akka.stream.actor.ActorPublisherMessage.Request

trait CurrentEventsQueries {
  this: BaseEventsPublisher =>

  private var recoveryCompleted: Boolean = false

  protected def currentEventsBehavior: Receive = {
    case Request(_) =>
      deliverBuf()
      if (recoveryCompleted && buf.isEmpty) {
        log.debug(s"Stopping after recovery completed and buffer empty")
        onCompleteThenStop()
      }

    case RecoverySuccess(highestSequenceNr) =>
      deliverBuf()
      if (highestSequenceNr > currOffset) {
        log.debug(s"Keep replaying since highestSequenceNr=$highestSequenceNr > currOffset=$currOffset")
        replay()
      } else {
        log.debug("Recovering to highestSequenceNr completed.")
        recoveryCompleted = true
      }
  }

}
