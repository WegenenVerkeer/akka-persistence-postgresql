package akka.persistence.pg.journal.query

import akka.actor.Props
import akka.persistence.pg.EventTag
import scala.concurrent.duration.FiniteDuration

private[akka] object EventsByTagsPublisher {

  def props(tags: Set[EventTag],
            fromOffset: Long,
            toOffset: Long,
            refreshInterval: FiniteDuration,
            maxBufSize: Int,
            writeJournalPluginId: String): Props = {

    Props(new LiveEventsByTagsPublisher(tags, fromOffset, toOffset, refreshInterval, maxBufSize, writeJournalPluginId))
  }
}
