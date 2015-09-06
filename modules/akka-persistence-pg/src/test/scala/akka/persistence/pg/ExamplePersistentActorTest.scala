package akka.persistence.pg

import java.util.UUID

import akka.actor.Props
import akka.persistence.pg.util.{RecreateSchema, PersistentActorTest}
import akka.persistence.{SnapshotOffer, PersistentActor}
import com.typesafe.config.{ConfigFactory, Config}
import org.scalatest.{BeforeAndAfterAll, ShouldMatchers}
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
    with RecreateSchema
    with ShouldMatchers
    with BeforeAndAfterAll
    with PgConfig {

  override val config: Config = ConfigFactory.load("example-actor-test.conf")
  override val pluginConfig = PluginConfig(config)
  val schemaName = config.getString("postgres.schema")

  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)))

  import driver.api._

  val id = UUID.randomUUID().toString
  val countEvents = sql"""select count(*) from "#$schemaName".journal where persistenceid = $id""".as[Long]
  val countSnapshots = sql"""select count(*) from "#$schemaName".snapshot where persistenceid = $id""".as[Long]

  val createJournal = sqlu"""create table "#$schemaName".journal (
                           "id" BIGSERIAL NOT NULL PRIMARY KEY,
                           "persistenceid" VARCHAR(254) NOT NULL,
                           "sequencenr" INT NOT NULL,
                           "partitionkey" VARCHAR(254) DEFAULT NULL,
                           "deleted" BOOLEAN DEFAULT false,
                           "sender" VARCHAR(512),
                           "payload" BYTEA,
                           "payloadmf" VARCHAR(512),
                           "uuid" VARCHAR(254) NOT NULL,
                           "created" timestamptz NOT NULL,
                           "tags" HSTORE,
                           "event" JSON,
                           constraint "cc_journal_payload_event" check (payload IS NOT NULL OR event IS NOT NULL))"""

  val createSnapshot = sqlu"""create table "#$schemaName".snapshot ("persistenceid" VARCHAR(254) NOT NULL,
                            "sequencenr" INT NOT NULL,
                            "partitionkey" VARCHAR(254) DEFAULT NULL,
                            "timestamp" bigint NOT NULL,
                            "snapshot" BYTEA,
                            PRIMARY KEY (persistenceid, sequencenr))"""


  /**
   * recreate schema and tables before running the tests
   */
  override def beforeAll() {
    Await.result(database.run(
      recreateSchema
        .andThen(createJournal).andThen(createSnapshot)
    ), 10 seconds)
    ()
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
