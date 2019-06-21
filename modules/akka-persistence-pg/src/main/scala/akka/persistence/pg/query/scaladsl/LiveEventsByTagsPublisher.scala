package akka.persistence.pg.query.scaladsl

import akka.persistence.pg.EventTag
import akka.persistence.pg.journal.PgAsyncWriteJournal._
import akka.persistence.query.{EventEnvelope, Offset}

import scala.concurrent.duration.FiniteDuration

class LiveEventsByTagsPublisher(
    tags: Set[EventTag],
    fromOffset: Long,
    toOffset: Long,
    refreshInterval: FiniteDuration,
    maxBufSize: Int,
    writeJournalPluginId: String
) extends BaseEventsPublisher(fromOffset, toOffset, refreshInterval, maxBufSize, writeJournalPluginId) {

  override def subscribe(): Unit =
    journal ! SubscribeTags(tags)

  def requestReplayFromJournal(limit: Int): Unit =
    journal ! ReplayTaggedMessages(currOffset, toOffset, limit, tags, self)

  override def replaying: Receive = super.replaying orElse {

    case ReplayedTaggedMessage(persistentRepr, _, offset) =>
      log.debug(s"Received replayed message: ${persistentRepr.persistenceId}")
      buf :+= EventEnvelope(
        offset = Offset.sequence(offset),
        persistenceId = persistentRepr.persistenceId,
        sequenceNr = persistentRepr.sequenceNr,
        event = persistentRepr.payload
      )
      currOffset = offset + 1
      deliverBuf()

  }

}
