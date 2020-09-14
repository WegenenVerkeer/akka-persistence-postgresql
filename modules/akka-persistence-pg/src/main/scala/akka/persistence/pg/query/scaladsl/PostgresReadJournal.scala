package akka.persistence.pg.query.scaladsl

import java.net.URLEncoder

import akka.NotUsed
import akka.actor.ExtendedActorSystem
import akka.persistence.pg.journal.PgAsyncWriteJournal.CancelSubscribers
import akka.persistence.pg.journal.ReadJournalStore
import akka.persistence.pg.{EventTag, PgConfig, PgExtension, PluginConfig}
import akka.persistence.pg.streams.EventsPublisherGraphStage
import akka.persistence.query.scaladsl._
import akka.persistence.query.{EventEnvelope, Offset}
import akka.persistence.{Persistence, PersistentRepr}
import akka.serialization.{Serialization, SerializationExtension}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.Config

class PostgresReadJournal(system: ExtendedActorSystem, config: Config)
    extends ReadJournal
    with ReadJournalStore
    with PgConfig
    with EventsByTags
    with AllEvents
    with EventsByPersistenceIdQuery
    with CurrentEventsByTags
    with CurrentAllEvents
    with CurrentEventsByPersistenceIdQuery
    with CurrentPersistenceIdsQuery {

  private val writeJournalPluginId: String = config.getString("write-plugin")
  private val maxBufSize: Int              = config.getInt("max-buffer-size")
  private val eventAdapters                = Persistence(system).adaptersFor(writeJournalPluginId)

  override lazy val pluginConfig: PluginConfig = pgExtension.pluginConfig
  override val serialization: Serialization    = SerializationExtension(system)
  override val pgExtension: PgExtension        = PgExtension(system)

  override def eventsByTags(
      tags: Set[EventTag],
      fromRowId: Long,
      toRowId: Long = Long.MaxValue
  ): Source[EventEnvelope, NotUsed] = {
    val encodedTags = URLEncoder.encode(tags.mkString("-"), ByteString.UTF_8)
    Source
      .fromMaterializer { (mat, _) =>
        Source.fromGraph(
          EventsPublisherGraphStage.byTags(fromRowId, toRowId, maxBufSize, writeJournalPluginId, tags)(mat)
        )
      }
      .named(s"eventsByTags-$encodedTags")
      .mapMaterializedValue(_ => NotUsed)
  }

  override def allEvents(fromRowId: Long, toRowId: Long = Long.MaxValue): Source[EventEnvelope, NotUsed] =
    Source
      .fromMaterializer { (mat, _) =>
        Source.fromGraph(
          EventsPublisherGraphStage.allEvents(fromRowId, toRowId, maxBufSize, writeJournalPluginId)(mat)
        )
      }
      .named(s"events-")
      .mapMaterializedValue(_ => NotUsed)

  override def eventsByPersistenceId(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long
  ): Source[EventEnvelope, NotUsed] =
    Source
      .fromMaterializer { (mat, _) =>
        Source.fromGraph(
          EventsPublisherGraphStage
            .byPersistenceId(fromSequenceNr, toSequenceNr, maxBufSize, writeJournalPluginId, persistenceId)(mat)
        )
      }
      .named(s"eventsByPersistenceId-${URLEncoder.encode(persistenceId, ByteString.UTF_8)}")
      .mapMaterializedValue(_ => NotUsed)

  override def currentEventsByTags(
      tags: Set[EventTag],
      fromSequenceNr: Long,
      toSequenceNr: Long
  ): Source[EventEnvelope, NotUsed] =
    currentEvents(fromSequenceNr, toSequenceNr, Some(tags))
      .mapConcat(adaptEvents)
      .map(persistentReprToEventEnvelope)

  override def currentAllEvents(
      fromSequenceNr: Long,
      toSequenceNr: Long = Long.MaxValue
  ): Source[EventEnvelope, NotUsed] =
    currentEvents(fromSequenceNr, toSequenceNr, None)
      .mapConcat(adaptEvents)
      .map(persistentReprToEventEnvelope)

  override def currentEventsByPersistenceId(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long
  ): Source[EventEnvelope, NotUsed] =
    currentEvents(persistenceId, fromSequenceNr, toSequenceNr)
      .mapConcat(adaptEvents)
      .map(persistentReprToEventEnvelope)

  def cancelAll(): Unit =
    Persistence(system).journalFor(writeJournalPluginId) ! CancelSubscribers

  private def adaptEvents(repr: PersistentRepr): List[PersistentRepr] = {
    val adapter = eventAdapters.get(repr.payload.getClass)
    adapter.fromJournal(repr.payload, repr.manifest).events.map(repr.withPayload).toList
  }

  private def persistentReprToEventEnvelope(persistentRepr: PersistentRepr) =
    new EventEnvelope(
      Offset.sequence(persistentRepr.sequenceNr),
      persistentRepr.persistenceId,
      persistentRepr.sequenceNr,
      persistentRepr.payload,
      0L
    )
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
