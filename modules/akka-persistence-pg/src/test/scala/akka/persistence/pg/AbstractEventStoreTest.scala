package akka.persistence.pg

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.pg.event._
import akka.persistence.pg.journal.JournalTable
import akka.persistence.pg.journal.query.PostgresReadJournal
import akka.persistence.pg.snapshot.SnapshotTable
import akka.persistence.pg.util.{CreateTables, RecreateSchema}
import akka.persistence.query.PersistenceQuery
import akka.stream.scaladsl.Source
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Seconds, Span}

import scala.language.postfixOps

abstract class AbstractEventStoreTest
  extends FunSuite
  with BeforeAndAfterEach
  with Matchers
  with BeforeAndAfterAll
  with JournalTable
  with SnapshotTable
  with EventStore
  with RecreateSchema
  with CreateTables
  with PgConfig
  with ScalaFutures {

  lazy val config = ConfigFactory.load("pg-eventstore.conf")
  implicit val system = ActorSystem("EventStoreTest", config)

  override lazy val pluginConfig = PgExtension(system).pluginConfig

  val testProbe = TestProbe()

  override implicit val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Milliseconds))

  import driver.api._

  override def beforeAll() {
    database.run(recreateSchema.andThen(createTables)).futureValue
    ()
  }

  override protected def beforeEach(): Unit = {
    database.run(DBIO.seq(
      journals.delete,
      snapshots.delete
    )).futureValue
  }

  override protected def afterAll(): Unit = {
    system.terminate()
    system.whenTerminated.futureValue
    ()
  }

  def startSource(tags: Set[EventTag], fromRowId: Long): Source[TestActor.Event, NotUsed] = {

    val readJournal =
      PersistenceQuery(system)
        .readJournalFor[PostgresReadJournal](PostgresReadJournal.Identifier)

    readJournal.eventsByTags(tags, fromRowId).map { env =>
      // and this will blow up if something different than a DomainEvent comes in!!
      env.event match {
        case evt: TestActor.Event => evt
        case unexpected => sys.error(s"Oeps!! That's was totally unexpected $unexpected")
      }
    }
  }

  def startSource(fromRowId: Long): Source[TestActor.Event, NotUsed] = {

    val readJournal =
      PersistenceQuery(system)
        .readJournalFor[PostgresReadJournal](PostgresReadJournal.Identifier)

    readJournal.events(fromRowId).map { env =>
      env.event match {
        case evt: TestActor.Event => evt
        case unexpected => sys.error(s"Oeps!! That's was totally unexpected $unexpected")
      }
    }
  }

  def startSource(persistenceId: String, fromRowId: Long): Source[TestActor.Event, NotUsed] = {

    val readJournal =
      PersistenceQuery(system)
        .readJournalFor[PostgresReadJournal](PostgresReadJournal.Identifier)

    readJournal.eventsByPersistenceId(persistenceId, fromRowId, Long.MaxValue).map { env =>
      env.event match {
        case evt: TestActor.Event => evt
        case unexpected => sys.error(s"Oeps!! That's was totally unexpected $unexpected")
      }
    }
  }

}
