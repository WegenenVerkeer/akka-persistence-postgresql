package akka.persistence.pg.journal.query

import akka.actor.{ActorRef, ActorLogging}
import akka.persistence.JournalProtocol.{RecoverySuccess, ReplayMessagesFailure}
import akka.persistence.{PersistentRepr, Persistence}
import akka.persistence.pg.journal.PgAsyncWriteJournal._
import akka.persistence.query.EventEnvelope
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import scala.concurrent.duration.FiniteDuration
import akka.persistence.pg.EventTag

class LiveEventsByTagsPublisher(tags: Set[EventTag],
                                fromOffset: Long,
                                toOffset: Long,
                                refreshInterval: FiniteDuration,
                                maxBufSize: Int,
                                writeJournalPluginId: String)
  extends ActorPublisher[EventEnvelope] with DeliveryBuffer[EventEnvelope] with ActorLogging {

  import LiveEventsByTagsPublisher._

  val tickTask = context.system.scheduler.schedule(refreshInterval, refreshInterval, self, Continue)(context.dispatcher)

  override def postStop(): Unit = {
    tickTask.cancel()
    ()
  }

  val journal: ActorRef = Persistence(context.system).journalFor(writeJournalPluginId)

  var currOffset = fromOffset

  var newEventsWhileReplaying = false

  def receive = init

  def init: Receive = {
    case _: Request => receiveInitialRequest()
    case Continue   => // skip, wait for first Request
    case Cancel     => context.stop(self)
  }

  def receiveInitialRequest(): Unit = {
    log.debug(s"received initial request -> subscribing to tags: $tags")
    journal ! SubscribeTags(tags)
    replay()
  }


  def replay(): Unit = {
    // reset flag
    newEventsWhileReplaying = false
    val limit = maxBufSize - buf.size
    log.debug(s"request replay for tag [{}] from [{}] to [{}]", tags, currOffset, toOffset)
    journal ! ReplayTaggedMessages(currOffset, toOffset, limit, tags, self)
    context become replaying
  }

  def replaying: Receive = {


    case ReplayedTaggedMessage(persistentRepr, _, offset) =>
      log.debug(s"Received replayed message: ${persistentRepr.persistenceId}")
      buf :+= EventEnvelope(
        offset = offset,
        persistenceId = persistentRepr.persistenceId,
        sequenceNr = persistentRepr.sequenceNr,
        event = persistentRepr.payload
      )
      currOffset = offset + 1
      deliverBuf()

    case RecoverySuccess(highestRowId) =>
      log.debug("replay completed for tag [{}], currOffset [{}]", tags, currOffset)
      receiveRecoverySuccess(highestRowId)

    case ReplayMessagesFailure(cause) =>
      log.debug("replay failed for tag [{}], due to [{}]", tags, cause.getMessage)
      deliverBuf()
      onErrorThenStop(cause)

    case _: Request =>
      deliverBuf()

    case Continue => // skip during replay

    case _: TaggedEventAppended =>
      // save row id so know if we can ask a replay as soon as possible
      newEventsWhileReplaying = true


    case Cancel =>
      context.stop(self)

    case e => log.debug(s"Got something unexpected!! $e")
  }


  def idle: Receive = {
    case Continue | _: TaggedEventAppended =>
      if (timeForReplay)
        replay()

    case _: Request =>
      receiveIdleRequest()

    case Cancel =>
      context.stop(self)
  }

  def timeForReplay: Boolean =
    (buf.isEmpty || buf.size <= maxBufSize / 2) && (currOffset <= toOffset)

  def receiveIdleRequest(): Unit = {
    deliverBuf()
    if (buf.isEmpty && currOffset > toOffset) {
      log.debug(s"stopping while idle: buffer is empty and $currOffset > $toOffset")
      onCompleteThenStop()
    }
  }

  def receiveRecoverySuccess(highestRowId: Long): Unit = {
    deliverBuf()
    if (buf.isEmpty && currOffset > toOffset) {
      log.debug(s"stopping after recovery: buffer is empty and $currOffset > $toOffset")
      onCompleteThenStop()
    }
    if (newEventsWhileReplaying) replay()
    else context.become(idle)
  }

}

object LiveEventsByTagsPublisher {

  private case object Continue

}