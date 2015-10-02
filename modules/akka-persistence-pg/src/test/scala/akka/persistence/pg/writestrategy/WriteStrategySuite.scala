package akka.persistence.pg.writestrategy

import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicLong, AtomicInteger}

import akka.pattern.{ask, pipe}
import akka.actor._
import akka.persistence.pg.event._
import akka.persistence.pg.journal.{JournalStore, NotPartitioned}
import akka.persistence.pg.perf.{PerfEventEncoder, PerfActor}
import akka.persistence.pg.util.RecreateSchema
import akka.persistence.pg.{PgConfig, PgExtension, PluginConfig}
import akka.persistence.PersistentActor
import akka.serialization.{Serialization, SerializationExtension}
import akka.util.Timeout
import com.typesafe.config.Config
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json._

import scala.concurrent.{Promise, Future, Await}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Try, Random}

abstract class WriteStrategySuite(config: Config) extends FunSuite
  with BeforeAndAfterEach
  with ShouldMatchers
  with BeforeAndAfterAll
  with JournalStore
  with EventStore
  with RecreateSchema
  with PgConfig
  with ScalaFutures {

  val system =  ActorSystem("TestCluster", config)
  override val schemaName = config.getString("postgres.schema")
  override val serialization: Serialization = SerializationExtension(system)
  override val pgExtension: PgExtension = PgExtension(system)
  override val pluginConfig = PluginConfig(system)
  override val eventEncoder = new PerfEventEncoder()
  override val eventTagger = DefaultTagger
  override val partitioner = NotPartitioned

  import driver.api._
  import PerfActor._
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val timeOut = Timeout(1, TimeUnit.MINUTES)
  val actors = 1 to 10 map { _ => system.actorOf(PerfActor.props) }
  val expected = 500


  def writeEvents(): Seq[Long] = {
    val received: AtomicInteger = new AtomicInteger(0)
    val eventReader = system.actorOf(Props(new EventReader()))

    1 to expected foreach { i =>
      actors(Random.nextInt(actors.size)) ? Alter(Random.alphanumeric.take(16).toString()) map { case s =>
        received.incrementAndGet()
      }
    }

    while (received.get() != expected) Thread.sleep(100L)

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

  override def beforeAll() {
    journals.schema.createStatements.foreach(println)

    Await.result(database.run(
      recreateSchema.andThen(journals.schema.create).andThen(sqlu"""create sequence #${pluginConfig.fullRowIdSequenceName}""")
    ), 10 seconds)
    super.beforeAll()
  }

  override protected def beforeEach(): Unit = {
    Await.result(database.run(DBIO.seq(journals.delete)), 10 seconds)
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

class RandomDelayStore(override val pluginConfig: PluginConfig) extends EventStore with PgConfig {

  import driver.api._
  import scala.concurrent.ExecutionContext.Implicits.global

  def delay() = {
    Try(Await.ready(Promise().future, Random.nextInt(50) millis))
  }

  override def postStoreActions(events: Seq[StoredEvent]): Seq[driver.api.DBIO[_]] = {
    Seq(DBIO.from(Future { delay(); () }))
  }
}

