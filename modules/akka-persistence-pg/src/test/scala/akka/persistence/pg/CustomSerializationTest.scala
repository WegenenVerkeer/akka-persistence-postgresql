package akka.persistence.pg

import java.util.UUID

import akka.actor.Props
import akka.persistence.pg.CustomSerializationTest.{
  ExamplePA,
  GetData,
  IntCommand,
  IntEvent,
  MyData,
  StringCommand,
  StringEvent,
  TakeSnapshot
}
import akka.persistence.pg.journal.JournalTable
import akka.persistence.{PersistentActor, SnapshotOffer}
import akka.persistence.pg.util.{CreateTables, PersistentActorTest, RecreateSchema}
import akka.serialization.SerializerWithStringManifest
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}

class CustomSerializationTest
    extends PersistentActorTest
    with ScalaFutures
    with Eventually
    with RecreateSchema
    with Matchers
    with BeforeAndAfterAll
    with JournalTable
    with CreateTables
    with PgConfig {

  override def config: Config = ConfigFactory.load("example-actor-serialization-test.conf")
  override def pluginConfig   = PluginConfig(config)

  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)))

  import driver.api._

  /**
    * recreate schema and tables before running the tests
    */
  override def beforeAll() {
    database.run(recreateSchema.andThen(createTables)).futureValue
    ()
  }

  override protected def afterAll(): Unit = database.close()

  val id = UUID.randomUUID().toString

  test("check journal entries are stored with custom serialization") { db =>
    db.run(countEvents(id)).futureValue shouldEqual 0
    db.run(countSnapshots(id)).futureValue shouldEqual 0

    val actor = system.actorOf(Props(new ExamplePA(id)))

    testProbe.send(actor, StringCommand("foo"))
    testProbe.expectMsg[String]("foo")

    testProbe.send(actor, StringCommand("bar"))
    testProbe.expectMsg[String]("bar")

    testProbe.send(actor, IntCommand(564))
    testProbe.expectMsg[Int](564)

    testProbe.send(actor, GetData)
    testProbe.expectMsg[MyData](MyData("bar", 564))

    db.run(countEvents(id)).futureValue shouldEqual 3
    db.run(countSnapshots(id)).futureValue shouldEqual 0

    val events = db
      .run(
        journals
          .sortBy(_.sequenceNr)
          .map(r => (r.sequenceNr, r.manifest, r.payload.get))
          .result
      )
      .futureValue

    events(0)._2 shouldBe "666:string_event"
    events(0)._3 should contain theSameElementsAs "foo".getBytes("UTF-8")
    events(1)._2 shouldBe "666:string_event"
    events(1)._3 should contain theSameElementsAs "bar".getBytes("UTF-8")
    events(2)._2 shouldBe "666:int_event"
    events(2)._3 should contain theSameElementsAs BigInt(564).toByteArray

  }

  test("check recovery of events") { db =>
    db.run(countEvents(id)).futureValue shouldEqual 0
    db.run(countSnapshots(id)).futureValue shouldEqual 0

    val actor = system.actorOf(Props(new ExamplePA(id)))

    testProbe.send(actor, StringCommand("foo"))
    testProbe.expectMsg[String]("foo")
    testProbe.send(actor, IntCommand(999))
    testProbe.expectMsg[Int](999)

    db.run(countEvents(id)).futureValue shouldEqual 2

    //stop the actor
    system.stop(actor)
    testProbe watch actor
    testProbe.expectTerminated(actor)

    //send message again, don't expect an answer because actor is down
    testProbe.send(actor, GetData)
    testProbe.expectNoMessage()

    val recovered = system.actorOf(Props(new ExamplePA(id)))
    testProbe.send(recovered, GetData)
    testProbe.expectMsg[MyData](MyData("foo", 999))

    db.run(countEvents(id)).futureValue shouldEqual 2
    db.run(countSnapshots(id)).futureValue shouldEqual 0
  }

  test("check snapshot is stored using custom serialization") { db =>
    db.run(countEvents(id)).futureValue shouldEqual 0
    db.run(countSnapshots(id)).futureValue shouldEqual 0

    val actor = system.actorOf(Props(new ExamplePA(id)))

    //send single event
    testProbe.send(actor, StringCommand("foo"))
    testProbe.expectMsg[String]("foo")
    testProbe.send(actor, IntCommand(321))
    testProbe.expectMsg[Int](321)

    testProbe.send(actor, TakeSnapshot)
    testProbe.expectNoMessage()

    eventually {
      db.run(countSnapshots(id)).futureValue shouldEqual 1
    }
    db.run(countEvents(id)).futureValue shouldEqual 2

    //stop the actor
    system.stop(actor)
    testProbe watch actor
    testProbe.expectTerminated(actor)

    val recovered = system.actorOf(Props(new ExamplePA(id)))
    testProbe.send(recovered, GetData)
    testProbe.expectMsg[MyData](MyData("foo", 321))
  }

}

object CustomSerializationTest {

  case class StringCommand(message: String)
  case class IntCommand(message: Int)
  case class StringEvent(message: String)
  case class IntEvent(message: Int)
  case object GetData
  case object TakeSnapshot

  case class MyData(string: String, int: Int)

  class ExamplePA(override val persistenceId: String) extends PersistentActor {

    var data: MyData = MyData("", 0)

    override def receiveRecover: Receive = {
      case StringEvent(message)              => data = data.copy(string = message)
      case IntEvent(message)                 => data = data.copy(int = message)
      case SnapshotOffer(metadata, snapshot) => data = snapshot.asInstanceOf[MyData]
    }

    override def receiveCommand: Receive = {
      case StringCommand(message) =>
        persist(StringEvent(message)) { e =>
          data = data.copy(string = message)
          sender ! message
        }
      case IntCommand(message) =>
        persist(IntEvent(message)) { e =>
          data = data.copy(int = message)
          sender ! message
        }

      case GetData      => sender ! data
      case TakeSnapshot => saveSnapshot(data)
    }

  }

}

class EventSerializer extends SerializerWithStringManifest {

  override def identifier: Int = 666

  override def manifest(o: AnyRef): String = o match {
    case StringEvent(_) => "string_event"
    case IntEvent(_)    => "int_event"
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case StringEvent(message) => message.getBytes("UTF-8")
    case IntEvent(message)    => BigInt(message).toByteArray
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case "string_event" => StringEvent(new String(bytes, "UTF-8"))
    case "int_event"    => IntEvent(BigInt(bytes).toInt)
  }
}

class SnapshotSerializer extends SerializerWithStringManifest {

  override def identifier: Int = 667

  override def manifest(o: AnyRef): String = "data_snapshot"

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case MyData(s, i) => s"$i:$s".getBytes("UTF-8")
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    val snapshot = new String(bytes, "UTF-8")
    val i        = snapshot.indexOf(':')
    MyData(snapshot.substring(i + 1), snapshot.substring(0, i).toInt)
  }
}
