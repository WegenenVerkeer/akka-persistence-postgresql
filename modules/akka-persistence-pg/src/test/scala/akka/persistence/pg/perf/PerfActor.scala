package akka.persistence.pg.perf

import java.util.UUID

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import akka.persistence.pg.perf.Messages.{Alter, Altered}

class PerfActor extends PersistentActor with ActorLogging {

  override val persistenceId: String = "TestActor_" + UUID.randomUUID().toString

  override def receiveRecover: Receive = { case _ => }

  override def receiveCommand: Receive = {
    case Alter(txt) =>
      persist(Altered(txt, System.currentTimeMillis())) { _ =>
        sender ! txt
      }
  }

}

object PerfActor {
  def props = Props(new PerfActor)
}
