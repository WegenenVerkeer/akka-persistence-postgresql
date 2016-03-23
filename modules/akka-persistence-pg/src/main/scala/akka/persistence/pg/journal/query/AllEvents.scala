package akka.persistence.pg.journal.query

import akka.NotUsed
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl.ReadJournal
import akka.stream.scaladsl.Source

trait AllEvents extends ReadJournal {

  def events(fromRowId: Long, toRowId: Long = Long.MaxValue): Source[EventEnvelope, NotUsed]

}
