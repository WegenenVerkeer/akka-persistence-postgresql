package akka.persistence.pg.query.scaladsl

import akka.actor.{ActorLogging, ActorRef}
import akka.persistence.JournalProtocol.{RecoverySuccess, ReplayMessagesFailure}
import akka.persistence.Persistence
import akka.persistence.pg.journal.PgAsyncWriteJournal._
import akka.persistence.query.EventEnvelope
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}

import scala.concurrent.duration.FiniteDuration

// FIXME needs a be rewritten as a GraphStage (since 2.5.0)
abstract class BaseEventsPublisher(
    fromOffset: Long,
    toOffset: Long,
    refreshInterval: FiniteDuration,
    maxBufSize: Int,
    writeJournalPluginId: String
) extends ActorPublisher[EventEnvelope]
    with DeliveryBuffer[EventEnvelope]
    with ActorLogging {

  private[akka] object Continue

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
    case request: Request =>
      log.debug(s"Received first request: $request")
      receiveInitialRequest()
    case Continue =>
    // skip, wait for first Request
    case Cancel =>
      val origSender = sender()
      log.debug(s"Received a 'Cancel' message from $origSender")
      context.stop(self)
  }

  def receiveInitialRequest(): Unit = {
    log.debug(s"received initial request -> subscribing to all events")
    subscribe()
    replay()
  }

  def subscribe(): Unit

  def replay(): Unit = {
    // reset flag
    newEventsWhileReplaying = false
    val limit = maxBufSize - buf.size
    log.debug("request replay from [{}] to [{}]", currOffset, toOffset)
    context become replaying
    requestReplayFromJournal(limit)
  }

  def requestReplayFromJournal(limit: Int): Unit

  def replaying: Receive = {

    case RecoverySuccess(highestRowId) =>
      log.debug(s"completed currOffset [$currOffset]")
      receiveRecoverySuccess(highestRowId)

    case ReplayMessagesFailure(cause) =>
      log.error(cause, s"replay failed due to [${cause.getMessage}]")
      deliverBuf()
      onErrorThenStop(cause)

    case request: Request =>
      log.debug(s"Received request: $request")
      deliverBuf()

    case Continue => // skip during replay

    case Cancel =>
      val origSender = sender()
      log.info(s"Streaming cancelled: $origSender")
      context.stop(self)

    case NewEventAppended =>
      // save row id so know if we can ask a replay as soon as possible
      log.debug(s"Received event notification while replaying")
      newEventsWhileReplaying = true
  }

  def idle: Receive = {
    case Continue | NewEventAppended => if (timeForReplay) replay()
    case _: Request                  => receiveIdleRequest()
    case Cancel                      => context.stop(self)
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
    } else {
      if (newEventsWhileReplaying || highestRowId > currOffset) replay()
      else context.become(idle)
    }
  }

  override def unhandled(message: Any): Unit =
    log.warning(s"Got unexpected message: $message")
}
