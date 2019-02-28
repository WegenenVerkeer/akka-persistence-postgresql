package akka.persistence.pg.query.javadsl

import java.util.{Set => JSet}

import akka.NotUsed
import akka.persistence.pg.EventTag
import akka.persistence.query.EventEnvelope
import akka.persistence.query.javadsl.ReadJournal
import akka.stream.javadsl.Source

trait EventsByTags extends ReadJournal {

  def eventsByTags(tags: JSet[EventTag], fromRowId: Long, toRowId: Long): Source[EventEnvelope, NotUsed]

}
