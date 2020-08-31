package akka.persistence.pg.streams

import akka.actor.Actor.Receive
import akka.actor.Status.Failure
import akka.actor.{ActorRef, Terminated}
import akka.persistence.JournalProtocol.{RecoverySuccess, ReplayMessagesFailure}
import akka.persistence.Persistence
import akka.persistence.pg.journal.PgAsyncWriteJournal.NewEventAppended
import akka.persistence.query.EventEnvelope
import akka.stream.{ActorMaterializerHelper, Materializer, SourceShape}
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.stage.{GraphStageLogic, OutHandler, StageLogging}

import scala.collection.{mutable => mut}

private[pg] object EventsPublisherStageLogic {

  class UnhandledMessageException(ref: ActorRef, msg: Any) extends RuntimeException(s"Unhandled message $msg from $ref")

  case object CancelEventsStage

}

private[pg] abstract class EventsPublisherStageLogic(
    shape: SourceShape[EventEnvelope],
    writeJournalPluginId: String,
    maxBufferSize: Int,
    fromOffset: Long,
    toOffset: Long
)(implicit materializer: Materializer)
    extends GraphStageLogic(shape)
    with StageLogging
    with OutHandler {
  import EventsPublisherStageLogic._

  val journal: ActorRef = Persistence(
    ActorMaterializerHelper.downcast(materializer).system
  ).journalFor(writeJournalPluginId)

  implicit protected lazy val sender: ActorRef = stageActor.ref

  protected var currentOffset: Long      = fromOffset
  private var failure: Option[Throwable] = None
  private val buffer                     = mut.Queue[EventEnvelope]()
  private var newEventsCount             = 0L
  private var initialized                = false
  private var done                       = false

  override def preStart(): Unit =
    stageActorBecome(idle).watch(journal)

  final override def onPull(): Unit = {
    if (!initialized) {
      initialize()
    }
    tryPush()
    if (buffer.isEmpty) {
      failure.foreach(failStage)
    }
  }

  private val idle: Receive = PartialFunction.empty

  private def replayingBase: Receive = {
    case CancelEventsStage =>
      log.debug(s"Events publisher was stopped")
      completeStage()

    case RecoverySuccess(highestSequenceNr) =>
      log.debug(s"completed currOffset [$currentOffset]")
      processRecoverySuccess(highestSequenceNr)

    case ReplayMessagesFailure(cause) =>
      log.error(cause, s"replay failed due to [${cause.getMessage}]")
      failure = Some(cause)

    case NewEventAppended =>
      log.debug(s"Received event notification while replaying")
      processNewEvent()
  }

  protected def enqueue(envelope: EventEnvelope): Unit = {
    buffer.enqueue(envelope)
    tryPush()
  }

  private def tryPush(): Unit =
    if (isAvailable(shape.out) && buffer.nonEmpty) {
      push(shape.out, buffer.dequeue())
    }

  private def processRecoverySuccess(highestSequenceNr: Long): Unit =
    if (currentOffset > toOffset && buffer.isEmpty) {
      completeStage()
    } else {
      if (highestSequenceNr > currentOffset || newEventsCount > 0) {
        requestNewEvents()
      } else {
        done = true
      }
    }

  private def processNewEvent(): Unit = {
    newEventsCount += 1
    if (done) {
      requestNewEvents()
      done = false
    }
  }

  private def requestNewEvents(): Unit = {
    val requestedAmount = maxBufferSize - buffer.size
    requestReplay(requestedAmount)
    newEventsCount = math.max(0, newEventsCount - requestedAmount)
  }

  private def initialize(): Unit = {
    subscribe()
    requestReplay(maxBufferSize)
    stageActorBecome(replayingReceive)
    initialized = true
  }

  private def stageActorBecome(receive: Receive): StageActor = getStageActor {
    case (_, msg) if receive.isDefinedAt(msg) => receive(msg)
    case (_, Terminated(`journal`))           => completeStage()
    case (_, Failure(cause))                  => failure = Some(cause)
    case (r, m)                               => failStage(new UnhandledMessageException(r, m))
  }

  def subscribe(): Unit
  def requestReplay(limit: Int): Unit
  def replaying: PartialFunction[Any, EventEnvelope]

  private def replayingReceive: Receive = replayingBase orElse (replaying andThen enqueue _)

  setHandler(shape.out, this)
}
