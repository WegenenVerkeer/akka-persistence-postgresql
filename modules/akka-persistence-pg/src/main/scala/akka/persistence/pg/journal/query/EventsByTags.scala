package akka.persistence.pg.journal.query

import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl.ReadJournal
import akka.stream.scaladsl.Source
import akka.persistence.pg.EventTag

trait EventsByTags extends ReadJournal {

  def eventsByTags(tags: Set[EventTag], fromRowId: Long, toRowId: Long): Source[EventEnvelope, Unit]

}
