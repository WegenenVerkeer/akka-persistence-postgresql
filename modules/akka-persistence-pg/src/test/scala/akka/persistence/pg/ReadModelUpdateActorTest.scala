package akka.persistence.pg

import akka.persistence.pg.perf.Messages.Alter
import akka.persistence.pg.perf.ReadModelUpdateActor
import akka.persistence.pg.perf.ReadModelUpdateActor.TextNotUnique
import akka.persistence.pg.util.{CreateTables, PersistentActorTest, RecreateSchema}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers}

class ReadModelUpdateActorTest
    extends PersistentActorTest
    with ScalaFutures
    with Eventually
    with RecreateSchema
    with Matchers
    with BeforeAndAfterAll
    with CreateTables
    with PgConfig {

  override val config: Config = ConfigFactory.load("example-actor-test.conf")
  override val pluginConfig   = PluginConfig(config)

  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)))

  import driver.api._

  /**
    * recreate schema and tables before running the tests
    */
  override def beforeAll() {
    database
      .run(
        recreateSchema
          .andThen(createTables)
          .andThen(sqlu"""create table #$readModelTable (
                                                          "id" BIGSERIAL NOT NULL PRIMARY KEY,
                                                          "cnt" INTEGER,
                                                          "txt" VARCHAR(255) DEFAULT NULL)""")
          .andThen(sqlu"""CREATE unique INDEX readmodel_txt_idx ON #$readModelTable (txt)""")
      )
      .futureValue
    1 to 2 map { i =>
      database.run(sqlu"""insert into #$readModelTable values ($i, 0, null)""").futureValue
    }
    ()
  }

  override protected def afterAll(): Unit = database.close()

  val readModelTable        = pluginConfig.getFullName("READMODEL")
  val countReadModelEntries = sql"""select count(*) from #$readModelTable where txt is not NULL""".as[Long]

  test("check sending unique text messages should work") { db =>
    ReadModelUpdateActor.reset()
    db.run(countEvents).futureValue shouldEqual 0
    db.run(countSnapshots).futureValue shouldEqual 0
    db.run(countReadModelEntries).futureValue.head shouldBe 0

    val actor1 = system.actorOf(ReadModelUpdateActor.props(driver, readModelTable))
    val actor2 = system.actorOf(ReadModelUpdateActor.props(driver, readModelTable))

    testProbe.send(actor1, Alter("foo"))
    testProbe.expectMsg[String]("foo")

    testProbe.send(actor2, Alter("bar"))
    testProbe.expectMsg[String]("bar")

    db.run(countEvents).futureValue shouldEqual 2
    db.run(countSnapshots).futureValue shouldEqual 0
    db.run(countReadModelEntries).futureValue.head shouldBe 2
  }

  test("check sending non-unique text messages should not be allowed") { implicit db =>
    ReadModelUpdateActor.reset()
    db.run(countEvents).futureValue shouldEqual 0
    db.run(countSnapshots).futureValue shouldEqual 0
    db.run(countReadModelEntries).futureValue.head shouldBe 0

    val actor1 = system.actorOf(ReadModelUpdateActor.props(driver, readModelTable))
    val actor2 = system.actorOf(ReadModelUpdateActor.props(driver, readModelTable))

    testProbe.send(actor1, Alter("foo"))
    testProbe.expectMsg[String]("foo")

    val checkPoint = savepoint()

    testProbe.send(actor2, Alter("foo"))
    testProbe.expectMsg(TextNotUnique)

    rollback(checkPoint)
    db.run(countEvents).futureValue shouldEqual 1
    db.run(countSnapshots).futureValue shouldEqual 0
    db.run(countReadModelEntries).futureValue.head shouldBe 1

  }

}
