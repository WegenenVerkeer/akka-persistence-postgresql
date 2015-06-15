package akka.persistence.pg.event

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.pattern.pipe
import akka.persistence.Persistence
import akka.persistence.pg.{PgExtension, PgActorConfig}

import scala.concurrent.Future

case class ReplayEvents(fromId: Long,
                        tags: Map[String, String],
                        max: Long = Long.MaxValue)

/**
 * Reply message to a [[ReplayEvents]] request. A separate reply is sent to the requestor for each replayed event.
 * @param event replayed event.
 */
case class ReplayedEvent(event: Event)

/**
 * Reply message to a successful [[ReplayEvents]] request. This reply is sent to the requestor
 * after all [[ReplayedEvent]] have been sent (if any).
 */
case object ReplayEventsSuccess

/**
 * Reply message to a failed [[ReplayEvents]] request. This reply is sent to the requestor
 * if a replay could not be successfully completed.
 */
case class ReplayEventsFailure(cause: Throwable)

class PgEventReader extends Actor with EventStore with ActorLogging with PgActorConfig {

  implicit val system: ActorSystem = context.system
  implicit val executionContext = context.system.dispatcher

  override val pluginConfig = PgExtension(context.system).pluginConfig
  override val eventStoreConfig = pluginConfig.eventStoreConfig

  private val extension = Persistence(context.system)
  private val publish = extension.settings.internal.publishPluginCommands
  private val db = pluginConfig.database

  override def receive: Receive = {
    case r @ ReplayEvents(fromId, tags, max) =>
      val replyTo = sender()
      asyncReplayEvents(fromId, tags, max) { e =>
        replyTo ! ReplayedEvent(e)
      } map {
        _ => replyTo ! ReplayEventsSuccess
      } recover {
        case e ⇒ ReplayEventsFailure(e)
      } pipeTo replyTo onSuccess {
        case _ if publish ⇒ context.system.eventStream.publish(r)
      }
  }

  import akka.persistence.pg.PgPostgresDriver.api._

  def asyncReplayEvents(fromId: Long, tags: Map[String, String], max: Long = Long.MaxValue)
                                (replayCallback: Event => Unit): Future[Unit] = {
    db.run {
      findEvents(fromId, tags, max).result
    } map { events =>
      events.foreach(replayCallback)
    }
  }
  
}
