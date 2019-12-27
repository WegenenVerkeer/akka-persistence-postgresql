package akka.persistence.pg.streams

import akka.persistence.pg.journal.PgAsyncWriteJournal.{ReplayMessages, ReplayedEventMessage, SubscribeAllEvents}
import akka.persistence.query.{EventEnvelope, Offset}
import akka.stream.{Materializer, SourceShape}

private[pg] class AllEventsPublisherStageLogic(
    shape: SourceShape[EventEnvelope],
    fromOffset: Long,
    toOffset: Long,
    maxBufferSize: Int,
    writeJournalPluginId: String
)(implicit materializer: Materializer)
    extends EventsPublisherStageLogic(shape, writeJournalPluginId, maxBufferSize, fromOffset, toOffset) {

  override def subscribe(): Unit =
    journal ! SubscribeAllEvents

  override def requestReplay(limit: Int): Unit =
    journal ! ReplayMessages(currentOffset, toOffset, limit, sender)

  override def replaying: PartialFunction[Any, EventEnvelope] = {

    case ReplayedEventMessage(persistentRepr, offset) =>
      currentOffset = offset + 1
      EventEnvelope(
        offset = Offset.sequence(offset),
        persistenceId = persistentRepr.persistenceId,
        sequenceNr = persistentRepr.sequenceNr,
        event = persistentRepr.payload
      )

  }
}
