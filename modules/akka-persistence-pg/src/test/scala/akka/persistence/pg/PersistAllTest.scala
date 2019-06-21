package akka.persistence.pg

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import akka.pattern.ask
import akka.persistence.pg.perf.Messages.Alter
import akka.persistence.pg.perf.PersistAllActor
import akka.persistence.pg.util.{CreateTables, RecreateSchema}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Seconds, Span}

import scala.util.Random

class PersistAllTest
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

  val config                     = ConfigFactory.load("pg-persistall.conf")
  val system                     = ActorSystem("TestCluster", config)
  override lazy val pluginConfig = PgExtension(system).pluginConfig

  import driver.api._

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val timeOut      = Timeout(1, TimeUnit.MINUTES)
  val numActors             = 2
  var actors: Seq[ActorRef] = _
  val expected              = 10

  test("writing events should respect order") {
    writeEvents()
    database.run(countEvents).futureValue shouldBe expected * 10

    actors.zipWithIndex.foreach {
      case (actor, i) =>
        val persistenceId = s"PersistAllActor_${i + 1}"
        val r: Vector[(Long, Long)] = database
          .run(
            sql"""select id, rowid from #${pluginConfig.fullJournalTableName}
             where persistenceid = $persistenceId order by id asc""".as[(Long, Long)]
          )
          .futureValue

        //check if ids are sorted => of course they are
        val ids = r map { case (id, rowid) => id }
        ids shouldEqual ids.sorted

        //check if rowids are sorted
        val rowIds = r map { case (id, rowid) => rowid }
        rowIds shouldEqual rowIds.sorted
    }

  }

  def writeEvents() = {
    val received: AtomicInteger = new AtomicInteger(0)

    def sendMessage(i: Int) =
      actors(i) ? Alter(Random.alphanumeric.take(16).mkString) map {
        case s: String =>
          received.incrementAndGet()
      }

    1 to expected foreach { i =>
      sendMessage(Random.nextInt(actors.size))
    }

    waitUntilEventsWritten(expected, received)
  }

  override def beforeAll() {
    PersistAllActor.reset()
    database.run(recreateSchema.andThen(createTables)).futureValue
    actors = 1 to numActors map { i =>
      system.actorOf(PersistAllActor.props)
    }

  }

  override protected def afterAll(): Unit = {
    system.terminate()
    system.whenTerminated.futureValue
    ()
  }

}
