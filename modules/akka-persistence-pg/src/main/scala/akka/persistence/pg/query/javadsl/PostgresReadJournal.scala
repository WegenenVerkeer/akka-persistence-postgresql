package akka.persistence.pg.query.javadsl

import java.util.{Set => JSet}

import akka.NotUsed
import akka.persistence.pg.EventTag
import akka.persistence.pg.query.scaladsl.{PostgresReadJournal => ScalaPostgresReadJournal}
import akka.persistence.query.EventEnvelope
import akka.persistence.query.javadsl.{CurrentEventsByPersistenceIdQuery, EventsByPersistenceIdQuery, ReadJournal}
import akka.stream.javadsl.Source

import scala.collection.JavaConverters._

class PostgresReadJournal(journal: ScalaPostgresReadJournal)
    extends ReadJournal
    with EventsByTags
    with AllEvents
    with EventsByPersistenceIdQuery
    with CurrentEventsByTags
    with CurrentAllEvents
    with CurrentEventsByPersistenceIdQuery {

  override def eventsByTags(tags: JSet[EventTag], fromRowId: Long, toRowId: Long): Source[EventEnvelope, NotUsed] =
    journal.eventsByTags(tags.asScala.toSet, fromRowId, toRowId).asJava

  override def allEvents(fromRowId: Long, toRowId: Long): Source[EventEnvelope, NotUsed] =
    journal.allEvents(fromRowId, toRowId).asJava

  override def eventsByPersistenceId(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long
  ): Source[EventEnvelope, NotUsed] =
    journal.eventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava

  override def currentEventsByTags(
      tags: JSet[EventTag],
      fromRowId: Long,
      toRowId: Long
  ): Source[EventEnvelope, NotUsed] =
    journal.currentEventsByTags(tags.asScala.toSet, fromRowId, toRowId).asJava

  override def currentAllEvents(fromRowId: Long, toRowId: Long): Source[EventEnvelope, NotUsed] =
    journal.currentAllEvents(fromRowId, toRowId).asJava

  override def currentEventsByPersistenceId(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long
  ): Source[EventEnvelope, NotUsed] =
    journal.currentEventsByPersistenceId(persistenceId, fromSequenceNr, toSequenceNr).asJava
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
