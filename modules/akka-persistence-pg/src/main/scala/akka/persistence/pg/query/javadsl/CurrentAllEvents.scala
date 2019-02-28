package akka.persistence.pg.query.javadsl

import akka.NotUsed
import akka.persistence.query.EventEnvelope
import akka.persistence.query.javadsl.ReadJournal
import akka.stream.javadsl.Source

trait CurrentAllEvents extends ReadJournal {

  def currentAllEvents(fromRowId: Long, toRowId: Long = Long.MaxValue): Source[EventEnvelope, NotUsed]

}
