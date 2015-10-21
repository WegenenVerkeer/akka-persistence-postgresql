package be.wegenenverkeer.es

import java.util.UUID

import akka.actor.Props
import akka.persistence.pg.util.{CreateTables, PersistentActorTest, RecreateSchema}
import akka.persistence.pg.{PgConfig, PluginConfig}
import be.wegenenverkeer.es.AggregateRootSuite._
import be.wegenenverkeer.es.domain.AggregateRoot
import be.wegenenverkeer.es.domain.AggregateRoot.{Event, Metadata}
import com.typesafe.config.{Config, ConfigFactory}
import org.postgresql.util.PSQLException
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}

object AggregateRootSuite {
  case class MyData(name: String, value: String) extends AggregateRoot.Data
  case class Create(name: String, value: String, metadata: Map[String, String] = Map.empty) extends AggregateRoot.Command
  case class Created(name: String, value: String, metadata: Metadata) extends AggregateRoot.Event
  case class Modify(value: String, metadata: Map[String, String] = Map.empty) extends AggregateRoot.Command
  case class Modified(value: String, metadata: Metadata) extends AggregateRoot.Event
  case object AlreadyExists
}

class AggregateRootSuite extends PersistentActorTest
  with ScalaFutures
  with BeforeAndAfterAll
  with ShouldMatchers
  with RecreateSchema
  with CreateTables
  with PgConfig {

  override val config: Config = ConfigFactory.load("example-actor-test.conf")
  val pluginConfig = PluginConfig(config)
  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)))

  import driver.api._

  lazy val readModelTable = pluginConfig.getFullName("readmodel")

  override protected def beforeAll(): Unit = {
    database.run(recreateSchema
      .andThen(createTables)
      .andThen(sqlu"""create table #$readModelTable ("name" VARCHAR(254) NOT NULL PRIMARY KEY, "value" VARCHAR(254) NOT NULL)""")
    ).futureValue
    ()
  }


  val countReadModel = sql"""select count(*) from #$readModelTable""".as[Long].head
  val selectFromReadModel = sql"""select * from #$readModelTable""".as[(String, String)].head

  val uuid = UUID.randomUUID().toString

  test("creating non-existing aggregate with read-model update") { db =>
    db.run(countEvents).futureValue shouldEqual 0
    db.run(countReadModel).futureValue shouldEqual 0

    val actor = system.actorOf(Props(new MyAggregate(uuid)))
    testProbe.send(actor, Create("foo", "bar", Map.empty))
    val result = testProbe.expectMsgType[MyData]
    result.name shouldBe "foo"
    result.value shouldBe "bar"
    db.run(countEvents(uuid)).futureValue shouldEqual 1
    db.run(countReadModel).futureValue shouldBe 1
    db.run(selectFromReadModel).futureValue shouldBe (("foo", "bar"))
  }

  test("modifying non-exising aggregate fails") { db =>
    db.run(countEvents).futureValue shouldEqual 0
    db.run(countReadModel).futureValue shouldEqual 0

    val actor = system.actorOf(Props(new MyAggregate(uuid)))
    testProbe.send(actor, Modify("baz", Map.empty))
    testProbe.expectNoMsg

    db.run(countEvents(uuid)).futureValue shouldEqual 0
    db.run(countReadModel).futureValue shouldBe 0
  }

  test("update existing aggregate updates the read-model") { db =>
    db.run(countEvents).futureValue shouldEqual 0
    db.run(countReadModel).futureValue shouldEqual 0

    val actor = system.actorOf(Props(new MyAggregate(uuid)))
    testProbe.send(actor, Create("foo", "bar", Map.empty))
    testProbe.expectMsgType[MyData]

    testProbe.send(actor, Modify("baz"))
    testProbe.expectMsg(MyData("foo", "baz"))

    db.run(countEvents(uuid)).futureValue shouldEqual 2
    db.run(countReadModel).futureValue shouldBe 1
    db.run(selectFromReadModel).futureValue shouldBe (("foo", "baz"))
  }

  class MyAggregate(override val persistenceId: String) extends AggregateRoot[MyData] {

    override protected def initial: Receive = {

      case Create(name, v, metadata) =>
          persistCQRSEvent(
            Created(name, v, Metadata(persistenceId, 1, metadata)),
            Seq(sqlu"""insert into #$readModelTable values ($name, $v)""")
          )(e => afterEventPersisted(e),
            //http://www.postgresql.org/docs/9.4/static/errcodes-appendix.html
          { case t: PSQLException if t.getSQLState == "23505" => sender ! AlreadyExists })

    }

    override protected def created: Receive = {

      case Modify(value: String, metadata) =>
        persistCQRSEvent(Modified(value, Metadata(persistenceId, 1, metadata)),
          Seq(sqlu"""update #$readModelTable set value = $value where name = ${data.name}""")
        ) (e => afterEventPersisted(e))
    }

    override def updateState(event: Event): Unit = event match {

      case Created(name, v, metadata) =>
        data = MyData(name, v)
        context.become(created)

      case Modified(v, metadata) =>
        data = data.copy(value = v)
    }
  }


}
