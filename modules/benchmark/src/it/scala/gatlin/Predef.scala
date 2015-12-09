package gatlin

import akka.actor.ActorRef

object Predef {

  def request(message: AskMessage) = new AskActionBuilder(message)

}
