package akka.persistence.pg.event

import akka.actor.{ActorLogging, ActorRef, Cancellable}
import akka.persistence._
import akka.persistence.pg.{PluginConfig, PgExtension, PgConfig}

import scala.concurrent.duration.FiniteDuration

//TODO implement stashing if Update with await=true
trait PersistentEventView extends PersistentActor with PgConfig with ActorLogging {

  import context.dispatcher

  /**
   * a unique id for this PersistentEventView
   */
  def viewId: String

  /**
   * only view events which have the following tags
   */
  def tags: Map[String, String] = Map.empty

  def persistenceId: String = viewId //is the view id

  private val pgExtension: PgExtension = PgExtension(context.system)
  private val viewSettings = extension.settings.view

  private val eventReader: ActorRef = pgExtension.eventReader
  override val pluginConfig: PluginConfig = pgExtension.pluginConfig
  private val eventEncoder: JsonEncoder = pluginConfig.eventStoreConfig.eventEncoder

  /**
   * If `true`, this view automatically updates itself with an interval specified by `autoUpdateInterval`.
   * If `false`, applications must explicitly update this view by sending [[Update]] requests. The default
   * value can be configured with the `akka.persistence.view.auto-update` configuration key. This method
   * can be overridden by implementation classes to return non-default values.
   */
  def autoUpdate: Boolean = viewSettings.autoUpdate

  /**
   * The interval for automated updates. The default value can be configured with the
   * `akka.persistence.view.auto-update-interval` configuration key. This method can be
   * overridden by implementation classes to return non-default values.
   */
  def autoUpdateInterval: FiniteDuration = viewSettings.autoUpdateInterval

  /**
   * The maximum number of messages to replay per update. The default value can be configured with the
   * `akka.persistence.view.auto-update-replay-max` configuration key. This method can be overridden by
   * implementation classes to return non-default values.
   */
  def autoUpdateReplayMax: Long = viewSettings.autoUpdateReplayMax

  private var schedule: Option[Cancellable] = None

  var lastEventId: Long = 0L
  private var eventIdAtReplayStart: Long = 0L

  def receiveEvent(persistenceId: String, event: Any): Unit

  def processEvent(event: Event) = {
    val clazz = pgExtension.getClassFor[Any](event.className)
    val decoded = eventEncoder.fromJson((event.event, clazz))
    receiveEvent(event.persistenceId, decoded)
  }

  private def replayEvents(max: Long = Long.MaxValue) = {
    eventIdAtReplayStart = lastEventId
    eventReader ! ReplayEvents(lastEventId + 1L, tags, max)
  }

  override def receiveCommand: Receive = {
    case Update(_, max) => replayEvents(max)
    case ReplayedEvent(e) ⇒
      if (e.id > lastEventId) {
        processEvent(e)
        lastEventId = e.id
      } else {
        log.warning("already seen event with id = "+e.id)
      }
    case ReplayEventsFailure => onReplayCompleted()
    case ReplayEventsSuccess => onReplayCompleted()
    case SaveSnapshotSuccess(metadata) =>
      eventIdAtReplayStart = lastEventId
      deleteSnapshots(SnapshotSelectionCriteria(metadata.sequenceNr-1))
      updateLastSequenceNr(metadata.sequenceNr + 1)
  }

  override def receiveRecover: Receive = {
    case SnapshotOffer(metadata, snapshot) ⇒
      lastEventId = snapshot.asInstanceOf[Long]
      updateLastSequenceNr(metadata.sequenceNr + 1)
    case RecoveryCompleted ⇒
      if (autoUpdate) replayEvents()
  }

  def onReplayCompleted(): Unit = {
    if (lastEventId > eventIdAtReplayStart) {
      saveSnapshot(lastEventId)
    }
    if (autoUpdate) schedule = Some(context.system.scheduler.scheduleOnce(autoUpdateInterval, self, Update(await = false, autoUpdateReplayMax)))
  }

  override def postStop(): Unit = {
    schedule.foreach(_.cancel())
    super.postStop()
  }



}
