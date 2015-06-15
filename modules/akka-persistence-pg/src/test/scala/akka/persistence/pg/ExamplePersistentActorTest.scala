package akka.persistence.pg

import java.util.UUID

import akka.actor.{ActorRef, Props}
import akka.persistence.pg.util.{RecreateSchema, PersistentActorTest}
import akka.persistence.{SnapshotOffer, PersistentActor}
import com.typesafe.config.{ConfigFactory, Config}
import org.scalatest.ShouldMatchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}

case class Command(message: String)
case class Event(message: String)
case object GetMessage
case object TakeSnapshot

class ExamplePersistentActorTest extends PersistentActorTest
    with ScalaFutures
    with Eventually
    with ShouldMatchers {

  override val config: Config = ConfigFactory.load("actor-test.conf")
  val schemaName = config.getString("postgres.schema")

  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)))

  import akka.persistence.pg.PgPostgresDriver.api._

  val id = UUID.randomUUID().toString
  val countEvents = sql"""select count(*) from "#$schemaName".journal where persistenceid = $id""".as[Long]
  val countSnapshots = sql"""select count(*) from "#$schemaName".snapshot where persistenceid = $id""".as[Long]

  test("check journal entries are stored") { param =>
    param.db.run(countEvents).futureValue.head shouldEqual 0
    param.db.run(countSnapshots).futureValue.head shouldEqual 0

    val actor = system.actorOf(Props(new ExamplePA(id)))

    testProbe.send(actor, Command("foo"))
    testProbe.expectMsg[String]("oof")

    testProbe.send(actor, GetMessage)
    testProbe.expectMsg[String]("foo")

    testProbe.send(actor, Command("bar"))
    testProbe.expectMsg[String]("rab")

    testProbe.send(actor, GetMessage)
    testProbe.expectMsg[String]("bar")

    param.db.run(countEvents).futureValue.head shouldEqual 2
    param.db.run(countSnapshots).futureValue.head shouldEqual 0
  }

  test("check recovery of events") { param =>
    param.db.run(countEvents).futureValue.head shouldEqual 0
    param.db.run(countSnapshots).futureValue.head shouldEqual 0

    val actor = system.actorOf(Props(new ExamplePA(id)))

    testProbe.send(actor, Command("foo"))
    testProbe.expectMsg[String]("oof")

    param.db.run(countEvents).futureValue.head shouldEqual 1

    //stop the actor
    system.stop(actor)
    testProbe watch actor
    testProbe.expectTerminated(actor)

    //send message again, don't expect an answer because actor is down
    testProbe.send(actor, GetMessage)
    testProbe.expectNoMsg()

    val recovered = system.actorOf(Props(new ExamplePA(id)))
    testProbe.send(recovered, GetMessage)
    testProbe.expectMsg[String]("foo")

    param.db.run(countEvents).futureValue.head shouldEqual 1
    param.db.run(countSnapshots).futureValue.head shouldEqual 0
  }

  test("check snapshot is stored") { param =>
    param.db.run(countEvents).futureValue.head shouldEqual 0
    param.db.run(countSnapshots).futureValue.head shouldEqual 0

    val actor = system.actorOf(Props(new ExamplePA(id)))

    //send single event
    testProbe.send(actor, Command("foo"))
    testProbe.expectMsg[String]("oof")

    testProbe.send(actor, TakeSnapshot)
    testProbe.expectNoMsg

    eventually {
      param.db.run(countSnapshots).futureValue.head shouldEqual 1
    }
    param.db.run(countEvents).futureValue.head shouldEqual 1

    //stop the actor
    system.stop(actor)
    testProbe watch actor
    testProbe.expectTerminated(actor)

    val recovered = system.actorOf(Props(new ExamplePA(id)))
    testProbe.send(recovered, GetMessage)
    testProbe.expectMsg[String]("foo")
  }

  private class ExamplePA(override val persistenceId: String) extends PersistentActor {

    var currentMessage: Option[String] = None

    override def receiveRecover: Receive = {
      case Event(message) => currentMessage = Option(message)
      case SnapshotOffer(metadata, snapshot) => currentMessage = snapshot.asInstanceOf[Option[String]]
    }

    override def receiveCommand: Receive = {
      case Command(message) => persist(Event(message)) { e =>
        currentMessage = Some(message)
        sender ! message.reverse
      }
      case GetMessage => sender ! currentMessage.getOrElse(sys.error("message is not yet set"))
      case TakeSnapshot => println("saving snapshot"); saveSnapshot(currentMessage)
    }

  }

}
