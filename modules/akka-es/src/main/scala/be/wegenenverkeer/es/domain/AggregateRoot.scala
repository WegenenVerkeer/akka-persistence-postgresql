package be.wegenenverkeer.es.domain

import akka.actor._
import akka.event.{DiagnosticLoggingAdapter, Logging}
import akka.persistence._
import akka.persistence.pg.event.Tagged
import be.wegenenverkeer.es.domain.AggregateRoot._
import play.api.libs.json.Json

object AggregateRoot {

  /**
   * state of Aggregate Root
   * TODO: sealed of niet
   */
  trait State
  case object Uninitialized extends State
  case object Initialized  extends State
  case object Removed extends State

  /**
   * data of AR (only valid when state is Initialized 
   */
  trait Data

  /**
   * Base traits for all commands
   */
  trait Command {
    /**
     * metadata typically contains data from the end-user: who executed the command, with which browser, ...
     * @return the metadata
     */
    def metadata: Map[String, String]
  }

  case class Remove(metadata: Map[String, String] = Map.empty) extends Command

  /**
   * Command to retrieve the internal state of an AR
   * We are not interested in metadata of GET requests, so it's an empty Map
   */
  
  case object GetState extends Command {
    override val metadata: Map[String, String] = Map.empty
  }

  case class Exists(id: String) extends Command {
    override val metadata: Map[String, String] = Map.empty
  }

  case class ExistsResponse(id: String, exists: Boolean, replyTo: ActorRef)

  /**
   * Base trait for all events
   */
  trait Event {
    def metadata: Metadata
    def eventName: String = s"Onbekend event ${getClass.getName}"
    def eventOmschrijving: String = s"Onbekend event $toString"
    def eventOnderwerp: String = s"Onbekend event $toString"
  }

  case class Metadata(aggregateId: String,
                      version: Int,
                      attributes: Map[String, String])

  implicit val metadataFormat = Json.format[Metadata]

  /**
   * Represents an Event subclass with tags
   * @param event the wrapped event
   * @param tags the tags
   * @tparam E Event subclass
   */
  case class TaggedEvent[E <: Event](event: E, tags: Map[String, String]) extends Tagged[E]

  /**
   * Specifies how many events should be processed before new snapshot is taken.
   * TODO: make configurable
   */
  val eventsPerSnapshot = 10

}

/**
 * Base class for aggregates.
 * It includes such functionality as: snapshot management, publishing applied events to Event Bus, 
 * handling actor recovery (i.e. replaying events).
 *
 */
//TODO: GracefulPassivation => actor shutdown na xx minuten inactief
trait AggregateRoot[D <: Data] extends /*GracefulPassivation with */ PersistentActor with ActorLogging {

  /**
   * Id of the persistent entity for which messages should be replayed.
   */
  override def persistenceId: String

  /**
   * the lifecycle of the aggegrate, by default [[Uninitialized]]
   */
  protected var state: State = Uninitialized

  /**
   * The data of the aggregate, is only valid when aggregate's state is [[Initialized]]
   * So it can be null when the aggregate is in another state
   */
  protected var data: D = _

  private var eventsSinceLastSnapshot = 0

  override def receiveCommand = initial

  /**
   * PartialFunction to handle commands when the Actor is in the [[Uninitialized]] state
   */
  protected def initial: Receive

  protected def created: Receive

  /**
   * Asynchronously persists `event` and `tags`. On successful persistence, `handler` is called with the
   * persisted event. It is guaranteed that no new commands will be received by a persistent actor
   * between a call to `persist` and the execution of its `handler`. This also holds for
   * multiple `persist` calls per received command.
   * 
   * @param event the event to persist
   * @param tags the tags to persist
   * @param handler callback handler for each persisted `event`
   */
  protected def persistWithTags[E <: Event](event: E, tags: Map[String, String])(handler: E => Unit): Unit = {
    def taggedHandler(e: TaggedEvent[E]): Unit = {
        handler(e.event)
    }
    persist(TaggedEvent(event, tags))(taggedHandler)
  }

  /**
   * Updates internal aggregate state according to event that is to be applied.
   * You should override this method and update the internal state of your aggregate in accordance with each event.
   * This method is called both:
   *  - after an event is persisted by calling persist() or persistWithTags() in your code
   *  - when an event is replayed from the persistent log after an actor is (re-)started 
   * @param evt Event to apply
   */
  def updateState(evt: AggregateRoot.Event): Unit

  /**
   * Helper method to set the protected data, typically called from within the updateState method
   * @param d the data to set
   */
  protected def setData(d: D): Unit = {
    state = Initialized
    data = d
  }

  /**
   * This method should be used as a callback handler for persist() method.
   * It will:
   * - check if a snapshot needs to be saved.
   * - update the internal aggregate state, by calling updateState
   * - send a response with the aggregate's state to `replyTo` actor
   * - publish an event on the Akka event bus
   *
   * @param replyTo optional actorRef to send reply to, default is the current sender
   * @param evt Event that has been persisted
   */
  protected def afterEventPersisted(replyTo: ActorRef = context.sender())(evt: AggregateRoot.Event): Unit = {
    eventsSinceLastSnapshot += 1
    updateState(evt)
    if (eventsSinceLastSnapshot >= eventsPerSnapshot) {
      log.debug(s"$eventsPerSnapshot events reached, saving snapshot")
      saveSnapshot((state, data))
      eventsSinceLastSnapshot = 0
    }
    respond(replyTo)
    publish(evt)
  }

  /**
   * send a message containing the aggregate's state back to the requester
   * @param replyTo actor to send message to (by default the sender from where you received a command)
   */
  protected def respond(replyTo: ActorRef = context.sender()): Unit = {
    replyTo ! data
  }

  /**
   * publish event to akka's eventstream
   * @param event the AR event to publish
   */
  protected def publish(event: AggregateRoot.Event) =
    context.system.eventStream.publish(event)

  /**
   * Recovery handler that receives persisted events during recovery. If a state snapshot
   * has been captured and saved, this handler will receive a [[SnapshotOffer]] message
   * followed by events that are younger than the offered snapshot.
   *
   * This handler must not have side-effects other than changing persistent actor state i.e. it
   * should not perform actions that may fail, such as interacting with external services,
   * for example.
   *
   * If recovery fails, the actor will be stopped. This can be customized by
   * handling [[RecoveryFailure]].
   *
   * @see [[Recover]]
   */
  override val receiveRecover: Receive = {
    case evt: AggregateRoot.Event =>
      eventsSinceLastSnapshot += 1
      updateState(evt)
    case SnapshotOffer(metadata, (state: AggregateRoot.State, data: Any)) =>
      eventsSinceLastSnapshot = 0
      restoreState(metadata, state, data.asInstanceOf[D])
      log.debug("recovering aggregate from snapshot")
    case SaveSnapshotSuccess(metadata) =>
      log.debug("snapshot saved")
    case RecoveryCompleted =>
      log.debug(s"aggegrate '$persistenceId' has recovered, state = '$state'")
    case RecoveryFailure(t) =>
      throw new RuntimeException("Recovery failed, will be retried", t)

  }

  /**
   * restore the lifecyle and state of the aggregate from a snapshot 
   * @param metadata snapshot metadata
   * @param state the state of the aggregate
   * @param data the data of the aggregate
   */
  protected def restoreState(metadata: SnapshotMetadata,
                            state: AggregateRoot.State,
                            data: D) = {
    this.state = state
    this.state match {
      case Uninitialized => context.become(initial)
      case Initialized => context.become(created)
    }
    setData(data)
  }

}