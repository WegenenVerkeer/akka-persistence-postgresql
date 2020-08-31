package akka.persistence.pg.streams

import akka.persistence.pg.EventTag
import akka.persistence.query.EventEnvelope
import akka.stream.{Attributes, Materializer, Outlet, SourceShape}
import akka.stream.stage.GraphStage

object EventsPublisherGraphStage {

  def byPersistenceId(
      fromOffset: Long,
      toOffset: Long,
      maxBufferSize: Int,
      writeJournalPluginId: String,
      persistenceId: String
  )(implicit materializer: Materializer): GraphStage[SourceShape[EventEnvelope]] =
    new EventsPublisherGraphStage() {
      override def createLogic(inheritedAttributes: Attributes): EventsPublisherStageLogic =
        new EventsByPersistenceIdPublisherStageLogic(
          shape,
          persistenceId,
          writeJournalPluginId,
          maxBufferSize,
          fromOffset,
          toOffset
        )
    }

  def byTags(
      fromOffset: Long,
      toOffset: Long,
      maxBufferSize: Int,
      writeJournalPluginId: String,
      tags: Set[EventTag]
  )(implicit materializer: Materializer): GraphStage[SourceShape[EventEnvelope]] =
    new EventsPublisherGraphStage() {
      override def createLogic(inheritedAttributes: Attributes): EventsPublisherStageLogic =
        new EventsByTagsPublisherStageLogic(
          shape,
          tags,
          fromOffset,
          toOffset,
          maxBufferSize,
          writeJournalPluginId
        )
    }

  def allEvents(
      fromOffset: Long,
      toOffset: Long,
      maxBufferSize: Int,
      writeJournalPluginId: String
  )(implicit materializer: Materializer): GraphStage[SourceShape[EventEnvelope]] =
    new EventsPublisherGraphStage {
      override def createLogic(inheritedAttributes: Attributes): EventsPublisherStageLogic =
        new AllEventsPublisherStageLogic(
          shape,
          fromOffset,
          toOffset,
          maxBufferSize,
          writeJournalPluginId
        )
    }

}

private[pg] abstract class EventsPublisherGraphStage() extends GraphStage[SourceShape[EventEnvelope]] {

  val out: Outlet[EventEnvelope]        = Outlet("EventsPublisher.out")
  val shape: SourceShape[EventEnvelope] = SourceShape(out)

  def createLogic(inheritedAttributes: Attributes): EventsPublisherStageLogic

}
