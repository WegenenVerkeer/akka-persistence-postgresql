package akka.persistence.pg.journal

import akka.NotUsed
import akka.persistence.PersistentRepr
import akka.persistence.pg.{EventTag, PgConfig}
import akka.stream.scaladsl.Source
import slick.jdbc.{ResultSetConcurrency, ResultSetType}

trait ReadJournalStore extends JournalStore { self: PgConfig =>

  import driver.api._

  def currentEvents(
      persistenceId: String,
      fromSequenceNr: Long,
      toSequenceNr: Long
  ): Source[PersistentRepr, NotUsed] = {
    val query = journals
      .filter(_.persistenceId === persistenceId)
      .filter(_.idForQuery >= fromSequenceNr)
      .filter(_.idForQuery <= toSequenceNr)
      .sortBy(_.idForQuery)
      .result
      .withStatementParameters(
        rsType = ResultSetType.ForwardOnly,
        rsConcurrency = ResultSetConcurrency.ReadOnly,
        fetchSize = 1000
      )
      .transactionally

    val publisher = database.stream(query)
    Source.fromPublisher(publisher).map(toPersistentRepr)
  }

  def currentEvents(
      fromSequenceNr: Long,
      toSequenceNr: Long,
      maybeTags: Option[Set[EventTag]]
  ): Source[PersistentRepr, NotUsed] = {
    val tagFilter = maybeTags match {
      case Some(tags) => tagsFilter(tags)
      case None =>
        (_: JournalTable) => true: Rep[Boolean]
    }

    val query = journals
      .filter(_.idForQuery >= fromSequenceNr)
      .filter(_.idForQuery <= toSequenceNr)
      .filter(tagFilter)
      .sortBy(_.idForQuery)
      .result
      .withStatementParameters(
        rsType = ResultSetType.ForwardOnly,
        rsConcurrency = ResultSetConcurrency.ReadOnly,
        fetchSize = 1000
      )
      .transactionally

    val publisher = database.stream(query)
    Source.fromPublisher(publisher).map(toPersistentRepr)
  }

}
