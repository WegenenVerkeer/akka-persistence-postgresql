package akka.persistence.pg

import akka.actor.Props
import akka.persistence.pg.query.scaladsl.{
  LiveEventsByPersistenceIdPublisher,
  LiveEventsByTagsPublisher,
  LiveEventsPublisher
}

import scala.concurrent.duration.FiniteDuration

package object query {

  private[akka] object EventsPublisher {

    def props(
        fromOffset: Long,
        toOffset: Long,
        refreshInterval: FiniteDuration,
        maxBufSize: Int,
        writeJournalPluginId: String
    ): Props =
      Props(new LiveEventsPublisher(fromOffset, toOffset, refreshInterval, maxBufSize, writeJournalPluginId))
  }

  private[akka] object EventsByTagsPublisher {

    def props(
        tags: Set[EventTag],
        fromOffset: Long,
        toOffset: Long,
        refreshInterval: FiniteDuration,
        maxBufSize: Int,
        writeJournalPluginId: String
    ): Props =
      Props(
        new LiveEventsByTagsPublisher(tags, fromOffset, toOffset, refreshInterval, maxBufSize, writeJournalPluginId)
      )
  }

  private[akka] object EventsByPersistenceIdPublisher {

    def props(
        persistenceId: String,
        fromOffset: Long,
        toOffset: Long,
        refreshInterval: FiniteDuration,
        maxBufSize: Int,
        writeJournalPluginId: String
    ): Props =
      Props(
        new LiveEventsByPersistenceIdPublisher(
          persistenceId,
          fromOffset,
          toOffset,
          refreshInterval,
          maxBufSize,
          writeJournalPluginId
        )
      )

  }

}
