package gatlin

import akka.actor.{Props, ActorRef}
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.config.Protocols

class AskActionBuilder(val actorRef: Option[ActorRef], val message: AskMessage) extends ActionBuilder {

  override def build(next: ActorRef, protocols: Protocols): ActorRef = {
    system.actorOf(Props(new AskAction(next, actorRef, message, "")))
  }
}
