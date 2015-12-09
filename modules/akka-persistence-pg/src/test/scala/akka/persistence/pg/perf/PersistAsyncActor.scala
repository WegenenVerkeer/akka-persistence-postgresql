package akka.persistence.pg.perf

import akka.actor.{Props, ActorLogging}
import akka.persistence.PersistentActor
import akka.persistence.pg.perf.Messages.{Alter, Altered}

class PersistAsyncActor(override val persistenceId: String) extends PersistentActor with ActorLogging {

  override def receiveRecover: Receive = { case _ => }

  override def receiveCommand: Receive = {
    case Alter(txt) =>
      val events = 1 to 10 map { i =>
        Altered(s"${txt}_$i")
      }
      persistAllAsync(events) { _ => sender ! txt }
  }

}

object PersistAsyncActor {
  def props(persistenceId: String) = Props(new PersistAsyncActor(persistenceId))
}



