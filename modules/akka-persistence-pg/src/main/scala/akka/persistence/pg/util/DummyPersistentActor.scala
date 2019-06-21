package akka.persistence.pg.util

import akka.actor.ActorLogging
import akka.persistence.PersistentActor

case object DummyCommand

class DummyPersistentActor extends PersistentActor with ActorLogging {

  override def receiveRecover: Receive = {
    case a: Any => log.debug("DummyPersistentActor receiveRecover received " + a)
  }

  override def receiveCommand: Receive = {
    case DummyCommand => log.debug("DummyPersistentActor received DummyCommand")
  }

  override def persistenceId: String = "pg-dummy"
}
