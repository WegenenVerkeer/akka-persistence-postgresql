package akka.persistence.pg.query.scaladsl

import akka.persistence.JournalProtocol.{ReplayMessages, ReplayedMessage}
import akka.persistence.pg.journal.PgAsyncWriteJournal._
import akka.persistence.query.{EventEnvelope, Offset}

import scala.concurrent.duration.FiniteDuration

class LiveEventsByPersistenceIdPublisher(
    persistenceId: String,
    fromOffset: Long,
    toOffset: Long,
    refreshInterval: FiniteDuration,
    maxBufSize: Int,
    writeJournalPluginId: String
) extends BaseEventsPublisher(fromOffset, toOffset, refreshInterval, maxBufSize, writeJournalPluginId) {

  override def subscribe(): Unit =
    journal ! SubscribePersistenceId(persistenceId)

  def requestReplayFromJournal(limit: Int): Unit =
    journal ! ReplayMessages(currOffset, toOffset, limit, persistenceId, self)

  override def replaying: Receive = super.replaying orElse {

    case ReplayedMessage(p) =>
      buf :+= EventEnvelope(
        offset = Offset.sequence(p.sequenceNr),
        persistenceId = persistenceId,
        sequenceNr = p.sequenceNr,
        event = p.payload
      )
      currOffset = p.sequenceNr + 1
      deliverBuf()

  }

}
