package akka.persistence.pg.journal.query

import java.net.URLEncoder

import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.persistence.Persistence
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.Config

import scala.concurrent.duration._
import akka.persistence.pg.EventTag
import akka.stream.actor.ActorPublisherMessage.Cancel
import akka.stream.impl.ActorPublisherSource
import akka.stream.impl.Stages.DefaultAttributes
import akka.stream.scaladsl.Source.shape

class PostgresReadJournal(system: ExtendedActorSystem, config: Config)
  extends ReadJournal
    with EventsByTags
    with AllEvents
    with EventsByPersistenceIdQuery
    with CurrentEventsByTags
    with CurrentAllEvents
    with CurrentEventsByPersistenceIdQuery {
  private val refreshInterval = config.getDuration("refresh-interval", MILLISECONDS).millis
  private val writeJournalPluginId: String = config.getString("write-plugin")
  private val maxBufSize: Int = config.getInt("max-buffer-size")

  override def eventsByTags(tags: Set[EventTag], fromRowId: Long, toRowId: Long = Long.MaxValue): Source[EventEnvelope, NotUsed] = {
    val encodedTags = URLEncoder.encode(tags.mkString("-"), ByteString.UTF_8)
    Source.fromGraph(new ActorPublisherSource(EventsByTagsPublisher.props(
      tags = tags,
      fromOffset = fromRowId,
      toOffset = toRowId,
      refreshInterval = refreshInterval,
      maxBufSize = maxBufSize,
      writeJournalPluginId = writeJournalPluginId
    ), DefaultAttributes.actorPublisherSource, shape(s"ActorPublisherSource-eventsByTags-$encodedTags")))
    .mapMaterializedValue(_ => NotUsed)
      .named(s"eventsByTags-$encodedTags")

  }

  override def allEvents(fromRowId: Long, toRowId: Long = Long.MaxValue): Source[EventEnvelope, NotUsed] = {
    Source.fromGraph(new ActorPublisherSource(EventsPublisher.props(
        fromOffset = fromRowId,
        toOffset = toRowId,
        refreshInterval = refreshInterval,
        maxBufSize = maxBufSize,
        writeJournalPluginId = writeJournalPluginId
    ), DefaultAttributes.actorPublisherSource, shape("ActorPublisherSource-allEvents")))
    .mapMaterializedValue(_ => NotUsed)
      .named("events-")

  }

  override def eventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] = {
    Source.fromGraph(new ActorPublisherSource(
      EventsByPersistenceIdPublisher.props(
        persistenceId = persistenceId,
        fromOffset = fromSequenceNr,
        toOffset = toSequenceNr,
        refreshInterval = refreshInterval,
        maxBufSize = maxBufSize,
        writeJournalPluginId = writeJournalPluginId
      ), DefaultAttributes.actorPublisherSource, shape(s"ActorPublisherSource-eventsByPersistenceId-${URLEncoder.encode(persistenceId, ByteString.UTF_8)}"))).mapMaterializedValue(_ => NotUsed)
      .named(s"eventsByPersistenceId-${URLEncoder.encode(persistenceId, ByteString.UTF_8)}")
  }

  override def currentEventsByTags(tags: Set[(String, String)], fromRowId: Long, toRowId: Long): Source[EventEnvelope, NotUsed] = {
    val encodedTags = URLEncoder.encode(tags.mkString("-"), ByteString.UTF_8)
    Source.fromGraph(new ActorPublisherSource(CurrentEventsByTagsPublisher.props(
      tags = tags,
      fromOffset = fromRowId,
      toOffset = toRowId,
      refreshInterval = refreshInterval,
      maxBufSize = maxBufSize,
      writeJournalPluginId = writeJournalPluginId
    ), DefaultAttributes.actorPublisherSource, shape(s"ActorPublisherSource-currentEventsByTags-$encodedTags")))
      .mapMaterializedValue(_ => NotUsed)
      .named(s"currentEventsByTags-$encodedTags")

  }

  override def currentAllEvents(fromRowId: Long, toRowId: Long = Long.MaxValue): Source[EventEnvelope, NotUsed] = {
    Source.fromGraph(new ActorPublisherSource(CurrentEventsPublisher.props(
      fromOffset = fromRowId,
      toOffset = toRowId,
      refreshInterval = refreshInterval,
      maxBufSize = maxBufSize,
      writeJournalPluginId = writeJournalPluginId
    ), DefaultAttributes.actorPublisherSource, shape("ActorPublisherSource-currentAllEvents")))
      .mapMaterializedValue(_ => NotUsed)
      .named("currentEvents-")
  }

  override def currentEventsByPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long): Source[EventEnvelope, NotUsed] = {
    Source.fromGraph(new ActorPublisherSource(
      CurrentEventsByPersistenceIdPublisher.props(
        persistenceId = persistenceId,
        fromOffset = fromSequenceNr,
        toOffset = toSequenceNr,
        refreshInterval = refreshInterval,
        maxBufSize = maxBufSize,
        writeJournalPluginId = writeJournalPluginId
      ), DefaultAttributes.actorPublisherSource, shape(s"ActorPublisherSource-currentEventsByPersistenceId-${URLEncoder.encode(persistenceId, ByteString.UTF_8)}"))).mapMaterializedValue(_ => NotUsed)
      .named(s"currentEventsByPersistenceId-${URLEncoder.encode(persistenceId, ByteString.UTF_8)}")
  }

  def cancelAll(): Unit = {
    Persistence(system).journalFor(writeJournalPluginId) ! Cancel
  }
}

object PostgresReadJournal {

  /**
   * The default identifier for [[PostgresReadJournal]] to be used with
   * [[akka.persistence.query.PersistenceQuery#readJournalFor]].
   *
   * The value is `"akka.persistence.pg.journal.query"` and corresponds
   * to the absolute path to the read journal configuration entry.
   */
  final val Identifier = "akka.persistence.pg.journal.query"

}
