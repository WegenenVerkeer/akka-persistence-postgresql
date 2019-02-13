package akka.persistence.pg.query.scaladsl

import akka.NotUsed
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl.ReadJournal
import akka.stream.scaladsl.Source

trait CurrentAllEvents extends ReadJournal {

  def currentAllEvents(fromRowId: Long, toRowId: Long = Long.MaxValue): Source[EventEnvelope, NotUsed]

}
