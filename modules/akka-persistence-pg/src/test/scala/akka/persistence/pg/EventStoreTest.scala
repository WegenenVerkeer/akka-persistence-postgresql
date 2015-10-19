package akka.persistence.pg

import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.pg.TestActor._
import akka.persistence.pg.event._
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

import scala.language.postfixOps

object EventStoreTest {
  val config = ConfigFactory.load("pg-eventstore.conf")
}

class EventStoreTest extends TestKit(ActorSystem("TestCluster", EventStoreTest.config))
  with FunSuiteLike with BeforeAndAfterEach with ShouldMatchers
  with BeforeAndAfterAll with JournalStore with EventStore with RecreateSchema
  with PgSnapshotStore
  with PgConfig
  with ScalaFutures {

  override val schemaName = EventStoreTest.config.getString("postgres.schema")

  override val serialization: Serialization = SerializationExtension(system)
  override val pgExtension: PgExtension = PgExtension(system)
  override val pluginConfig = PluginConfig(system)
  override val eventEncoder = new TestEventEncoder()
  override val eventTagger = DefaultTagger
  override val partitioner = NotPartitioned


  val testProbe = TestProbe()

  import driver.api._

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

    database.run(journals.size.result).futureValue shouldBe 2

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

  test("events implementing created use this as event's creation time"){
    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    testProbe.send(test, Alter("foo"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "foo"))

    database.run(events.size.result).futureValue shouldBe 1
    val storedEvent = database.run(events.result.head).futureValue
    (storedEvent.event \ "created").as[String] shouldBe DateTimeFormatter.ISO_DATE_TIME.format(storedEvent.created)
  }

  test("events NOT implementing created don't use this as event's creation time"){
    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    testProbe.send(test, Increment(5))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(count = 5))

    database.run(events.size.result).futureValue shouldBe 1
    val storedEvent = database.run(events.result.head).futureValue
    (storedEvent.event \ "created").as[String] shouldNot be(DateTimeFormatter.ISO_DATE_TIME.format(storedEvent.created))
  }

  test("generate snapshots"){
    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    testProbe.send(test, Alter("baz"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "baz"))

    testProbe.send(test, Snap)
    testProbe.expectMsg("s")

    database.run(events.size.result).futureValue shouldBe 1    //1 Alter event total
    database.run(snapshots.size.result).futureValue shouldBe 1 //1 snapshot stored
    database.run(journals.size.result).futureValue shouldBe 1  //1 journal message after the snapshot

    testProbe.send(test, Alter("foobar"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "foobar"))

    database.run(events.size.result).futureValue shouldBe 2    //2 Alter events total
    database.run(snapshots.size.result).futureValue shouldBe 1 //1 snapshot stored
    database.run(journals.size.result).futureValue shouldBe 2  //2 journal message

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

  override def beforeAll() {
    database.run(
      recreateSchema
        .andThen((journals.schema ++ snapshots.schema).create)
        .andThen(sqlu"""create sequence #${pluginConfig.fullRowIdSequenceName}""")
    ).futureValue
    super.beforeAll()
  }

  override protected def beforeEach(): Unit = {
    database.run(DBIO.seq(
      journals.delete,
      snapshots.delete
    )).futureValue
    super.beforeEach()
  }

}

object TestActor {
  case object Snap
  case object GetState
  case class Alter(id: String)
  case class Increment(count: Int)

  case class Altered(id: String, created: OffsetDateTime) extends Created
  case class Incremented(count: Int, created: OffsetDateTime)
  case class TheState(id: String = "", count: Int = 0)

}

class TestEventEncoder extends JsonEncoder {

  val A = classOf[Altered]
  val I = classOf[Incremented]

  override def toJson = {
    case a: Altered => JsObject(Seq("type" -> JsString("altered"),
      "id" -> JsString(a.id),
      "created" -> JsString(DateTimeFormatter.ISO_DATE_TIME.format(a.created))))
    case i: Incremented => JsObject(Seq("count" -> JsNumber(i.count),
      "created" -> JsString(DateTimeFormatter.ISO_DATE_TIME.format(i.created))))
  }

  def parseDateTime(json: JsValue): OffsetDateTime = {
    OffsetDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse((json \ "created").as[String]))
  }

  override def fromJson = {
    case(json, A) => Altered((json \ "id").as[String], parseDateTime(json))
    case(json, I) => Incremented((json \ "count").as[Int], parseDateTime(json))
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
    case m: Altered =>
      log.info("Recovering journal: {}", m)
      state = state.copy(id = m.id)
    case i: Incremented =>
      log.info("Recovering journal: {}", i)
      state = state.copy(count = state.count + i.count)
  }

  override def receiveCommand: Receive = {
    case a: Alter => persist(Altered(a.id, OffsetDateTime.now())) {
      case Altered(m, _) =>
        state = state.copy(id = m)
        testProbe ! "j"
    }
    case i: Increment => persist(Incremented(i.count, OffsetDateTime.now())) {
      case Incremented(c, _) =>
        state = state.copy(count = state.count + c)
        testProbe ! "j"
    }
    case Snap => saveSnapshot(state)
    case msg: SaveSnapshotFailure => testProbe ! "f"
    case msg: SaveSnapshotSuccess => testProbe ! "s"
    case GetState => sender ! state
  }
}

class TestEventStore(override val pluginConfig: PluginConfig) extends EventStore with PgConfig {

}



