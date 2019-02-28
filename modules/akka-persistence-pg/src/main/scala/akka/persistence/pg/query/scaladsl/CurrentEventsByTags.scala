package akka.persistence.pg.query.scaladsl

import akka.NotUsed
import akka.persistence.pg.EventTag
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl.ReadJournal
import akka.stream.scaladsl.Source

trait CurrentEventsByTags extends ReadJournal {

  def currentEventsByTags(tags: Set[EventTag], fromRowId: Long, toRowId: Long): Source[EventEnvelope, NotUsed]

}
