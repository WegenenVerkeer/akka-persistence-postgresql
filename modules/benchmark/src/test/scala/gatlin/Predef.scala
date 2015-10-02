package gatlin

import akka.actor.ActorRef

object Predef {

  def request(actorRef: ActorRef, message: AskMessage) = new AskActionBuilder(Some(actorRef), message)
  def request(message: AskMessage) = new AskActionBuilder(None, message)

}
