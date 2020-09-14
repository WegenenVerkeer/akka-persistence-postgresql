package akka.persistence.pg.streams

import akka.persistence.JournalProtocol.{ReplayMessages, ReplayedMessage}
import akka.persistence.pg.journal.PgAsyncWriteJournal.SubscribePersistenceId
import akka.persistence.query.{EventEnvelope, Offset}
import akka.stream.{Materializer, SourceShape}

private[pg] class EventsByPersistenceIdPublisherStageLogic(
    shape: SourceShape[EventEnvelope],
    persistenceId: String,
    writeJournalPluginId: String,
    maxBufferSize: Int,
    fromOffset: Long,
    toOffset: Long
)(implicit materializer: Materializer)
    extends EventsPublisherStageLogic(shape, writeJournalPluginId, maxBufferSize, fromOffset, toOffset) {

  override def subscribe(): Unit =
    journal ! SubscribePersistenceId(persistenceId)

  override def requestReplay(limit: Int): Unit =
    journal ! ReplayMessages(currentOffset, toOffset, limit, persistenceId, sender)

  override def replaying: PartialFunction[Any, EventEnvelope] = {
    case ReplayedMessage(p) =>
      currentOffset = p.sequenceNr + 1
      new EventEnvelope(
        offset = Offset.sequence(p.sequenceNr),
        persistenceId = persistenceId,
        sequenceNr = p.sequenceNr,
        event = p.payload,
        timestamp = 0L
      )
  }

}
