package akka.persistence.pg.query.scaladsl

import akka.NotUsed
import akka.persistence.pg.EventTag
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl.ReadJournal
import akka.stream.scaladsl.Source

trait EventsByTags extends ReadJournal {

  def eventsByTags(tags: Set[EventTag], fromRowId: Long, toRowId: Long): Source[EventEnvelope, NotUsed]

}
