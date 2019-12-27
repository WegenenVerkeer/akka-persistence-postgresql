package akka.persistence.pg.streams

import akka.persistence.pg.EventTag
import akka.persistence.pg.journal.PgAsyncWriteJournal.{ReplayTaggedMessages, ReplayedTaggedMessage, SubscribeTags}
import akka.persistence.query.{EventEnvelope, Offset}
import akka.stream.{Materializer, SourceShape}

private[pg] class EventsByTagsPublisherStageLogic(
    shape: SourceShape[EventEnvelope],
    tags: Set[EventTag],
    fromOffset: Long,
    toOffset: Long,
    maxBufferSize: Int,
    writeJournalPluginId: String
)(implicit materializer: Materializer)
    extends EventsPublisherStageLogic(shape, writeJournalPluginId, maxBufferSize, fromOffset, toOffset) {

  override def subscribe(): Unit =
    journal ! SubscribeTags(tags)

  override def requestReplay(limit: Int): Unit =
    journal ! ReplayTaggedMessages(currentOffset, toOffset, limit, tags, sender)

  override def replaying: PartialFunction[Any, EventEnvelope] = {

    case ReplayedTaggedMessage(persistentRepr, _, offset) =>
      currentOffset = offset + 1
      EventEnvelope(
        offset = Offset.sequence(offset),
        persistenceId = persistentRepr.persistenceId,
        sequenceNr = persistentRepr.sequenceNr,
        event = persistentRepr.payload
      )

  }
}
