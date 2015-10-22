package be.wegenenverkeer.es.domain

import akka.actor._
import akka.persistence._
import akka.persistence.pg.event.{ReadModelUpdates, Tagged}
import be.wegenenverkeer.es.actor.GracefulPassivation
import be.wegenenverkeer.es.domain.AggregateRoot._
import play.api.libs.json.Json
import slick.dbio.DBIO

object AggregateRoot {

  /**
   * state of Aggregate Root
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
  }

  case class Metadata(aggregateId: String,
                      version: Int,
                      attributes: Map[String, String])

  implicit val metadataFormat = Json.format[Metadata]

  /**
   * Represents an Event with tags
   * @param event the wrapped event
   * @param tags the tags
   * @tparam E Event subclass
   */
  case class TaggedEvent[E <: Event](event: E,
                                     tags: Map[String, String]) extends Tagged[E]

  /**
    * Represents an Event containing SQL statements to update the read-model in the same transaction as storing the event
    * Your read-model will be 'strict' consistent with your events
    * @param event the wrapped event
    * @param tags the tags
    * @param readModelUpdates the SQL statements to execute inside the tx that also stores the events
    * @param failureHandler a partial function, which can handle specific failures, originating from updating the read-model
    * @tparam E Event subclass
    */
  case class CQRSEvent[E <: Event](event: E,
                                   tags: Map[String, String],
                                   readModelUpdates: Seq[DBIO[_]],
                                   failureHandler: PartialFunction[Throwable, Unit]) extends Tagged[E] with ReadModelUpdates

  /**
   * Specifies how many events should be processed before new snapshot is taken.
   * TODO: make configurable
   */
  val eventsPerSnapshot = 10

}

/**
 * Base class for aggregate roots.
 * It includes such functionality as: snapshot management, publishing applied events to Event Bus, 
 * handling actor recovery (i.e. replaying events).
 */
trait AggregateRoot[D <: Data] extends GracefulPassivation with PersistentActor with ActorLogging {

  /**
   * Id of the persistent entity for which messages should be replayed.
   */
  override def persistenceId: String

  /**
   * the lifecycle of the aggregate, by default [[Uninitialized]]
   */
  protected var state: State = Uninitialized

  /**
   * The data of the aggregate, is only valid when the aggregate state is [[Initialized]]
   * So it can be null when the aggregate is in another state
   */
  protected var data: D = _

  private var eventsSinceLastSnapshot = 0

  override def receiveCommand = initial /*orElse {
    case PersistenceFailure(CQRSEvent(_, _, _, h), _, t) if h.isDefinedAt(t) => h(t)
  }*/

  /**
   * PartialFunction to handle commands when the Actor is in the [[Uninitialized]] state
   */
  protected def initial: Receive

  /**
    * PartialFunction to handle commands when the Actor is in the [[Initialized]] state
    */
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
  protected def persistWithTags[E <: Event](event: E,
                                            tags: Map[String, String])
                                           (handler: E => Unit): Unit = {
    def taggedHandler(e: TaggedEvent[E]): Unit = {
        handler(e.event)
    }
    persist(TaggedEvent(event, tags))(taggedHandler)
  }

  protected def persistCQRSEvent[E <: Event](event: E,
                                             readModelUpdates: Seq[DBIO[_]] = Seq.empty,
                                             tags: Map[String, String] = Map.empty)
                                            (handler: E => Unit,
                                             failureHandler: PartialFunction[Throwable, Unit] = PartialFunction.empty): Unit = {
    def wrappedHandler(e: CQRSEvent[E]): Unit = {
      handler(e.event)
    }
    persist(CQRSEvent(event, tags, readModelUpdates, failureHandler))(wrappedHandler)
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
   * - send a response with the aggregate state to `replyTo` actor
   * - publish an event on the Akka event bus
   *
   * @param replyTo optional actorRef to send reply to, default is the current sender
   * @param event Event that has been persisted
   */
  protected def afterEventPersisted(event: AggregateRoot.Event,
                                    replyTo: ActorRef = context.sender()): Unit = {
    eventsSinceLastSnapshot += 1
    updateState(event)
    if (eventsSinceLastSnapshot >= eventsPerSnapshot) {
      log.debug(s"$eventsPerSnapshot events reached, saving snapshot")
      saveSnapshot((state, data))
      eventsSinceLastSnapshot = 0
    }
    respond(replyTo)
    publish(event)
  }

  /**
   * send a message containing the aggregate's state back to the requester
   * @param replyTo actor to send message to (by default the sender from where you received a command)
   */
  protected def respond(replyTo: ActorRef = context.sender()): Unit = {
    replyTo ! data
  }

  /**
   * publish event to akka eventstream
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
   * @see [[Recovery]]
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
      log.debug(s"aggregate '$persistenceId' has recovered, state = '$state'")

  }

  /**
   * restore the lifecycle and state of the aggregate from a snapshot
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

  //TODO Persistent actor will be stopped => how to avoid, if we handle the persistence failure
//  override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
//    event match {
//      case CQRSEvent(_, _, _, h) if h.isDefinedAt(cause) => h(cause)
//      case _ => super.onPersistFailure(cause, event, seqNr)
//    }
//
//  }

}