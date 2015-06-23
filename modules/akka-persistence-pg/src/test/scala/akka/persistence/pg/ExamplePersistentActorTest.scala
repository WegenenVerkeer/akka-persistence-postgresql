package akka.persistence.pg

import java.util.UUID

import akka.actor.Props
import akka.persistence.pg.event.{NoneJsonEncoder, DefaultTagger, JsonEncoder, EventTagger}
import akka.persistence.pg.journal.{NotPartitioned, Partitioner, JournalStore}
import akka.persistence.pg.snapshot.PgSnapshotStore
import akka.persistence.pg.util.{RecreateSchema, PersistentActorTest}
import akka.persistence.{SnapshotOffer, PersistentActor}
import akka.serialization.{SerializationExtension, Serialization}
import com.typesafe.config.{ConfigFactory, Config}
import org.scalatest.ShouldMatchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.Await
import scala.concurrent.duration._

import scala.language.postfixOps

case class Command(message: String)
case class Event(message: String)
case object GetMessage
case object TakeSnapshot

class ExamplePersistentActorTest extends PersistentActorTest
    with ScalaFutures
    with Eventually
    with ShouldMatchers
    with RecreateSchema {

  override val config: Config = ConfigFactory.load("example-actor-test.conf")
  val schemaName = config.getString("postgres.schema")
  var schemaCreated = false

  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)))

  import akka.persistence.pg.PgPostgresDriver.api._

  val id = UUID.randomUUID().toString
  val countEvents = sql"""select count(*) from "#$schemaName".journal where persistenceid = $id""".as[Long]
  val countSnapshots = sql"""select count(*) from "#$schemaName".snapshot where persistenceid = $id""".as[Long]

  /**
   * we can't do this in beforeAll because we need actorSystem to access the PluginConfig and to get
   * the database and the 'journal' and 'snapshot' table DDL
   *
   * In a real test scenario it is best to create the schema and tables in a beforeAll or even before running your tests
   * and you should best do this through database evolutions scripts
   */
  override def beforeEach() {
    super.beforeEach() //this creates the actorSystem
    if (!schemaCreated) {
      new JournalStore with PgSnapshotStore {
        override val serialization: Serialization = SerializationExtension(system)
        override val pgExtension: PgExtension = PgExtension(system)
        override val pluginConfig = PluginConfig(system)
        override val eventEncoder = NoneJsonEncoder
        override val eventTagger = DefaultTagger
        override val partitioner = NotPartitioned

        override val db = pluginConfig.database

        Await.result(db.run(
          recreateSchema
            .andThen((journals.schema ++ snapshots.schema).create)
        ), 10 seconds)
      }
      schemaCreated = true
    }
  }


  test("check journal entries are stored") { db =>
    db.run(countEvents).futureValue.head shouldEqual 0
    db.run(countSnapshots).futureValue.head shouldEqual 0

    val actor = system.actorOf(Props(new ExamplePA(id)))

    testProbe.send(actor, Command("foo"))
    testProbe.expectMsg[String]("oof")

    testProbe.send(actor, GetMessage)
    testProbe.expectMsg[String]("foo")

    testProbe.send(actor, Command("bar"))
    testProbe.expectMsg[String]("rab")

    testProbe.send(actor, GetMessage)
    testProbe.expectMsg[String]("bar")

    db.run(countEvents).futureValue.head shouldEqual 2
    db.run(countSnapshots).futureValue.head shouldEqual 0
  }

  test("check recovery of events") { db =>
    db.run(countEvents).futureValue.head shouldEqual 0
    db.run(countSnapshots).futureValue.head shouldEqual 0

    val actor = system.actorOf(Props(new ExamplePA(id)))

    testProbe.send(actor, Command("foo"))
    testProbe.expectMsg[String]("oof")

    db.run(countEvents).futureValue.head shouldEqual 1

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

    db.run(countEvents).futureValue.head shouldEqual 1
    db.run(countSnapshots).futureValue.head shouldEqual 0
  }

  test("check snapshot is stored") { db =>
    db.run(countEvents).futureValue.head shouldEqual 0
    db.run(countSnapshots).futureValue.head shouldEqual 0

    val actor = system.actorOf(Props(new ExamplePA(id)))

    //send single event
    testProbe.send(actor, Command("foo"))
    testProbe.expectMsg[String]("oof")

    testProbe.send(actor, TakeSnapshot)
    testProbe.expectNoMsg

    eventually {
      db.run(countSnapshots).futureValue.head shouldEqual 1
    }
    db.run(countEvents).futureValue.head shouldEqual 1

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
      case TakeSnapshot => saveSnapshot(currentMessage)
    }

  }

}
