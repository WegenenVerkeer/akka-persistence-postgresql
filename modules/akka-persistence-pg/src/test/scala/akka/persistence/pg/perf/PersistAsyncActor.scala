package akka.persistence.pg.perf

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import akka.persistence.pg.perf.Messages.{Alter, Altered}

class PersistAsyncActor(override val persistenceId: String) extends PersistentActor with ActorLogging {

  override def receiveRecover: Receive = { case _ => }

  override def receiveCommand: Receive = {
    case Alter(txt) =>
      val created = System.currentTimeMillis()
      val events = 1 to 10 map { i =>
        Altered(s"${txt}_$i", created)
      }
      persistAllAsync(events) { _ =>
        sender ! txt
      }
  }

}

object PersistAsyncActor {
  def props(persistenceId: String) = Props(new PersistAsyncActor(persistenceId))
}
