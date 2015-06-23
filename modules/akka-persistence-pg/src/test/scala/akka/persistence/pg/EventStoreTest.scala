package akka.persistence.pg

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.pg.TestActor.{Alter, GetState, Snap, TheState}
import akka.persistence.pg.event.{DefaultTagger, EventStore, JsonEncoder}
import akka.persistence.pg.journal.{NotPartitioned, JournalStore}
import akka.persistence.pg.snapshot.PgSnapshotStore
import akka.persistence.pg.util.RecreateSchema
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import akka.serialization.{Serialization, SerializationExtension}
import akka.testkit.{TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json._
import slick.jdbc.JdbcBackend

import scala.concurrent.Await
import scala.concurrent.duration._

import scala.language.postfixOps

object EventStoreTest {
  val config = ConfigFactory.load("pg-eventstore.conf")
}

class EventStoreTest extends TestKit(ActorSystem("TestCluster", EventStoreTest.config))
  with FunSuiteLike with BeforeAndAfterEach with ShouldMatchers
  with BeforeAndAfterAll with JournalStore with EventStore with RecreateSchema
  with PgSnapshotStore
  with ScalaFutures {

  override val schemaName = EventStoreTest.config.getString("postgres.schema")

  override val serialization: Serialization = SerializationExtension(system)
  override val pgExtension: PgExtension = PgExtension(system)
  override val pluginConfig = PluginConfig(system)
  override val eventStoreConfig = pluginConfig.eventStoreConfig
  override val eventEncoder = new TestEventEncoder()
  override val eventTagger = DefaultTagger
  override val partitioner = NotPartitioned


  val testProbe = TestProbe()

  import akka.persistence.pg.PgPostgresDriver.api._

  test("generate events"){
    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    testProbe.send(test, Alter("foo"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "foo"))

    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "bar"))

    db.run(events.size.result).futureValue shouldBe 2
    db.run(journals.size.result).futureValue shouldBe 2

    // kill the actor
    system.stop(test)
    testProbe watch test
    testProbe.expectTerminated(test)

    // get persisted state
    val test2 = system.actorOf(Props(new TestActor(testProbe.ref)))
    testProbe.send(test2, GetState)
    testProbe.expectMsg(TheState(id = "bar"))

    system.stop(test2)
    testProbe watch test2
    testProbe.expectTerminated(test2)
    ()
  }

  test("generate snapshots"){
    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    testProbe.send(test, Alter("baz"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "baz"))

    testProbe.send(test, Snap)
    testProbe.expectMsg("s")

    db.run(events.size.result).futureValue shouldBe 1    //1 Alter event total
    db.run(snapshots.size.result).futureValue shouldBe 1 //1 snapshot stored
    db.run(journals.size.result).futureValue shouldBe 1  //1 journal message after the snapshot

    testProbe.send(test, Alter("foobar"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "foobar"))

    db.run(events.size.result).futureValue shouldBe 2    //2 Alter events total
    db.run(snapshots.size.result).futureValue shouldBe 1 //1 snapshot stored
    db.run(journals.size.result).futureValue shouldBe 2  //2 journal message

    // kill the actor
    system.stop(test)
    testProbe watch test
    testProbe.expectTerminated(test)

    // get persisted state
    val test2 = system.actorOf(Props(new TestActor(testProbe.ref)))
    testProbe.send(test2, GetState)
    testProbe.expectMsg(TheState(id = "foobar"))

    system.stop(test2)
    testProbe watch test2
    testProbe.expectTerminated(test2)
    ()
  }

  import akka.persistence.pg.PgPostgresDriver.api._
  override val db = pluginConfig.database

  override def beforeAll() {
    Await.result(db.run(
      recreateSchema
        .andThen((journals.schema ++ snapshots.schema).create)
    ), 10 seconds)
    super.beforeAll()
  }

  override protected def beforeEach(): Unit = {
    Await.result(db.run(DBIO.seq(
      journals.delete,
      snapshots.delete
    )), 10 seconds)
    super.beforeEach()
  }

}

object TestActor {
  case object Snap
  case object GetState
  case class Alter(id: String)
  case class TheState(id: String = "")

}

class TestEventEncoder extends JsonEncoder {

  val A = classOf[Alter]
  override def toJson = {
    case a: Alter => JsObject(Seq("type" -> JsString("altered"), "id" -> JsString(a.id)))
  }

  override def fromJson = {
    case(json, A) => Alter((json \ "id").as[String])
  }

}

class TestActor(testProbe: ActorRef) extends PersistentActor with ActorLogging {
  import akka.persistence.pg.TestActor._
  override def persistenceId: String = "TestActor"

  var state = TheState()

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, snap: TheState) =>
      log.info("Recovering snapshot: {}", snap)
      state = snap
    case m: Alter =>
      log.info("Recovering journal: {}", m)
      state = state.copy(id = m.id)
  }

  override def receiveCommand: Receive = {
    case a: Alter => persist(a) {
      case Alter(m) =>
        state = state.copy(id = m)
        testProbe ! "j"
    }
    case Snap => saveSnapshot(state)
    case msg: SaveSnapshotFailure => testProbe ! "f"
    case msg: SaveSnapshotSuccess => testProbe ! "s"
    case GetState => sender ! state
  }
}

class TestEventStore(override val db: JdbcBackend.Database, override val eventStoreConfig: EventStoreConfig) extends EventStore {

}



