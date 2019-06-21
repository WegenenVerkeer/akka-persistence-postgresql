package akka.persistence.pg.perf

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import akka.persistence.pg.perf.Messages.{Alter, Altered}

class PersistAllActor(id: Int) extends PersistentActor with ActorLogging {

  override val persistenceId: String = s"PersistAllActor_$id"

  override def receiveRecover: Receive = { case _ => }

  override def receiveCommand: Receive = {
    case Alter(txt) =>
      val created = System.currentTimeMillis()
      val events = 1 to 10 map { i =>
        Altered(s"${txt}_$i", created)
      }
      persistAll(events) { _ =>
        sender ! txt
      }
  }

}

object PersistAllActor {
  private val id = new AtomicInteger(0)
  def reset()    = id.set(0)

  def props = Props(new PersistAllActor(id.incrementAndGet()))
}
