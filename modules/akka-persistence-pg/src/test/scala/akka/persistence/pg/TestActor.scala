package akka.persistence.pg

import java.time.OffsetDateTime

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.pg.event.Created
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

class TestActor(testProbe: ActorRef, id: Option[String] = None) extends PersistentActor with ActorLogging {

  import akka.persistence.pg.TestActor._

  override def persistenceId: String = id.getOrElse(s"TestActor")

  var state = TheState()

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, snap: TheState) =>
      log.info("Recovering snapshot: {}", snap)
      state = snap
    case m: Altered =>
      log.info("Recovering journal: {}", m)
      state = state.copy(id = m.id)
    case i: Incremented =>
      log.info("Recovering journal: {}", i)
      state = state.copy(count = state.count + i.count)
  }

  override def receiveCommand: Receive = {
    case a: Alter =>
      persist(Altered(a.id, OffsetDateTime.now())) {
        case Altered(m, _) =>
          state = state.copy(id = m)
          testProbe ! "j"
      }
    case i: Increment =>
      persist(Incremented(i.count, OffsetDateTime.now())) {
        case Incremented(c, _) =>
          state = state.copy(count = state.count + c)
          testProbe ! "j"
      }
    case Snap                     => saveSnapshot(state)
    case msg: SaveSnapshotFailure => testProbe ! "f"
    case msg: SaveSnapshotSuccess => testProbe ! "s"
    case GetState                 => sender ! state
  }
}

object TestActor {

  def props(testProbe: ActorRef, persistenceId: Option[String] = None) = Props(new TestActor(testProbe, persistenceId))

  case object Snap

  case object GetState

  case class Alter(id: String)

  case class Increment(count: Int)

  sealed trait Event
  case class Altered(id: String, created: OffsetDateTime) extends Created with Event

  case class Incremented(count: Int, created: OffsetDateTime) extends Event

  case class TheState(id: String = "", count: Int = 0)

}
