package akka.persistence.pg

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import akka.actor._
import akka.pattern.ask
import akka.persistence.pg.perf.Messages.Alter
import akka.persistence.pg.perf.ReadModelUpdateActor
import akka.persistence.pg.util.{CreateTables, RecreateSchema}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Seconds, Span}

import scala.collection.JavaConverters._
import scala.util.Random

class ExtraDBIOSupportTest
    extends FunSuite
    with BeforeAndAfterEach
    with Matchers
    with BeforeAndAfterAll
    with CreateTables
    with RecreateSchema
    with PgConfig
    with WaitForEvents
    with ScalaFutures {

  override implicit val patienceConfig = PatienceConfig(timeout = Span(3, Seconds), interval = Span(100, Milliseconds))

  val config                     = ConfigFactory.load("pg-readmodelupdate.conf")
  val system                     = ActorSystem("TestCluster", config)
  override lazy val pluginConfig = PgExtension(system).pluginConfig

  import driver.api._

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val timeOut      = Timeout(1, TimeUnit.MINUTES)
  val numActors             = 20
  var actors: Seq[ActorRef] = _
  val expected              = 500
  val readModelTable        = pluginConfig.getFullName("READMODEL")

  test("writing events should update readmodel and not block") {
    val map = writeEvents()
    database.run(countEvents).futureValue shouldBe expected
    database
      .run(sql"""select count(*) from #$readModelTable where txt is not NULL""".as[Long])
      .futureValue
      .head shouldBe actors.size
    map.asScala.foreach {
      case (i, s) =>
        database.run(sql"""select txt from #$readModelTable where id = $i""".as[String]).futureValue.head shouldEqual s
    }
  }

  def writeEvents() = {
    val received: AtomicInteger             = new AtomicInteger(0)
    val map: ConcurrentHashMap[Int, String] = new ConcurrentHashMap()

    def sendMessage(i: Int) =
      actors(i) ? Alter(Random.alphanumeric.take(16).mkString) map {
        case s: String =>
          map.put(i + 1, s)
          received.incrementAndGet()
      }

    1 to expected foreach { i =>
      sendMessage(Random.nextInt(actors.size))
    }

    waitUntilEventsWritten(expected, received)

    map

  }

  override def beforeAll() {
    ReadModelUpdateActor.reset()
    database
      .run(
        recreateSchema.andThen(createTables).andThen(sqlu"""create table #$readModelTable (
                                                          "id" BIGSERIAL NOT NULL PRIMARY KEY,
                                                          "cnt" INTEGER,
                                                          "txt" VARCHAR(255) DEFAULT NULL)""")
      )
      .futureValue
    actors = 1 to numActors map { i =>
      database.run(sqlu"""  insert into #$readModelTable values ($i, 0, null)""").futureValue
      system.actorOf(ReadModelUpdateActor.props(driver, pluginConfig.getFullName("READMODEL")))
    }

  }

  override protected def afterAll(): Unit = {
    system.terminate()
    system.whenTerminated.futureValue
    ()
  }

}
