package akka.persistence.pg.writestrategy

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import akka.pattern.{ask, pipe}
import akka.persistence.pg.event._
import akka.persistence.pg.journal.JournalTable
import akka.persistence.pg.perf.{PerfActor, RandomDelayPerfActor}
import akka.persistence.pg.util.{CreateTables, RecreateSchema}
import akka.persistence.pg.{PgConfig, PluginConfig}
import akka.util.Timeout
import com.typesafe.config.Config
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Second, Span}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

abstract class WriteStrategySuite(config: Config) extends FunSuite
  with BeforeAndAfterEach
  with ShouldMatchers
  with BeforeAndAfterAll
  with JournalTable
  with RecreateSchema
  with CreateTables
  with PgConfig
  with ScalaFutures {

  val system =  ActorSystem("TestCluster", config)
  override val pluginConfig = PluginConfig(system)

  import PerfActor._
  import driver.api._

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val timeOut = Timeout(1, TimeUnit.MINUTES)
  var actors: Seq[ActorRef] = _
  val expected = 1000


  def writeEvents(): Seq[Long] = {
    val received: AtomicInteger = new AtomicInteger(0)
    val eventReader = system.actorOf(Props(new EventReader()))

    1 to expected foreach { i =>
      actors(Random.nextInt(actors.size)) ? Alter(Random.alphanumeric.take(16).toString()) map { case s =>
        received.incrementAndGet()
      }
    }

    var noProgressCount = 0
    var numEvents = received.get()
    while (numEvents != expected && noProgressCount < 50) {
      Thread.sleep(100L)
      val numExtra = received.get() - numEvents
      if (numExtra == 0) noProgressCount += 1
      else numEvents += numExtra
    }

    //just sleep a bit so the EventReader has seen the last events
    Thread.sleep(1000)
    Await.result((eventReader ? "stop").mapTo[Seq[Long]], 10 seconds)

  }

  def missingIds(ids: Seq[Long]): Seq[Long] = {
    var result: Seq[Long] = Seq.empty[Long]
    var prevId = 0L
    ids foreach { id: Long =>
      if (id != prevId + 1) {
        result = result :+ id
      }
      prevId = id
    }
    result
  }

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Second), interval = Span(100, Milliseconds))

  override def beforeAll() {
    database.run(
      recreateSchema.andThen(journals.schema.create).andThen(createRowIdSequence)
    ).futureValue
    actors = 1 to 10 map { _ => system.actorOf(RandomDelayPerfActor.props(driver)) }
  }

  override protected def beforeEach(): Unit = {
    database.run(DBIO.seq(journals.delete)).futureValue
    super.beforeEach()
  }

  class EventReader extends Actor {

    case class Retrieve(fromId: Long)
    case class EventIds(ids: Seq[Long])

    var running = true
    var ids: Seq[Long] = Seq.empty
    self ! Retrieve(0L)

    override def receive: Receive = {
      case Retrieve(fromId) if running =>
          database.run {
            pluginConfig.eventStore.get.findEvents(fromId).result
          } map {
            _ map {
              _.id
            }
          } map EventIds pipeTo self
          ()
      case EventIds(ids) => this.ids ++= ids
        val max = if (this.ids.isEmpty) 0 else this.ids.max + 1
        self ! Retrieve(max)
      case "stop" =>
        running = false
        sender ! ids
    }

  }

}

class DefaultEventStore(override val pluginConfig: PluginConfig) extends EventStore with PgConfig


