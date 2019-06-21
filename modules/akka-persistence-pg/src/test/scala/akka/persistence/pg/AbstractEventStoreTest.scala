package akka.persistence.pg

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.pg.event._
import akka.persistence.pg.journal.JournalTable
import akka.persistence.pg.query.scaladsl.PostgresReadJournal
import akka.persistence.pg.snapshot.SnapshotTable
import akka.persistence.pg.util.{CreateTables, RecreateSchema}
import akka.persistence.query.PersistenceQuery
import akka.stream.scaladsl.Source
import akka.testkit.TestProbe
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Seconds, Span}

import scala.reflect.ClassTag

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

  lazy val config: Config = ConfigFactory.load("pg-eventstore.conf")
  implicit val system     = ActorSystem("EventStoreTest", config)

  override lazy val pluginConfig: PluginConfig = PgExtension(system).pluginConfig

  val testProbe = TestProbe()

  override implicit val patienceConfig = PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Milliseconds))

  import driver.api._

  override def beforeAll() {
    database.run(recreateSchema.andThen(createTables)).futureValue
    ()
  }

  override protected def beforeEach(): Unit =
    PgExtension(system).whenDone {
      database.run(
        DBIO.seq(
          sqlu"""ALTER SEQUENCE #${pluginConfig.fullJournalTableName}_id_seq RESTART WITH 1""",
          journals.delete,
          snapshots.delete
        )
      )
    }.futureValue

  override protected def afterEach(): Unit =
    PersistenceQuery(system)
      .readJournalFor[PostgresReadJournal](PostgresReadJournal.Identifier)
      .cancelAll()

  override protected def afterAll(): Unit = {
    system.terminate()
    system.whenTerminated.futureValue
    ()
  }

  def startSource[E](tags: Set[EventTag], fromRowId: Long)(implicit tag: ClassTag[E]): Source[E, NotUsed] =
    PersistenceQuery(system)
      .readJournalFor[PostgresReadJournal](PostgresReadJournal.Identifier)
      .eventsByTags(tags, fromRowId)
      .map { env =>
        // and this will blow up if something different than a DomainEvent comes in!!
        env.event match {
          case evt: E     => evt
          case unexpected => sys.error(s"Oeps!! That's was totally unexpected $unexpected")
        }
      }

  def startSource[E](fromRowId: Long)(implicit tag: ClassTag[E]): Source[E, NotUsed] =
    PersistenceQuery(system)
      .readJournalFor[PostgresReadJournal](PostgresReadJournal.Identifier)
      .allEvents(fromRowId)
      .map { env =>
        env.event match {
          case evt: E     => evt
          case unexpected => sys.error(s"Oeps!! That's was totally unexpected $unexpected")
        }
      }

  def startSource[E](persistenceId: String, fromRowId: Long)(implicit tag: ClassTag[E]): Source[E, NotUsed] =
    PersistenceQuery(system)
      .readJournalFor[PostgresReadJournal](PostgresReadJournal.Identifier)
      .eventsByPersistenceId(persistenceId, fromRowId, Long.MaxValue)
      .map { env =>
        env.event match {
          case evt: E     => evt
          case unexpected => sys.error(s"Oeps!! That's was totally unexpected $unexpected")
        }
      }

  def startCurrentSource[E](tags: Set[EventTag], fromRowId: Long)(implicit tag: ClassTag[E]): Source[E, NotUsed] =
    PersistenceQuery(system)
      .readJournalFor[PostgresReadJournal](PostgresReadJournal.Identifier)
      .currentEventsByTags(tags, fromRowId, Long.MaxValue)
      .map { env =>
        // and this will blow up if something different than a DomainEvent comes in!!
        env.event match {
          case evt: E     => evt
          case unexpected => sys.error(s"Oeps!! That's was totally unexpected $unexpected")
        }
      }

  def startCurrentSource[E](fromRowId: Long)(implicit tag: ClassTag[E]): Source[E, NotUsed] =
    PersistenceQuery(system)
      .readJournalFor[PostgresReadJournal](PostgresReadJournal.Identifier)
      .currentAllEvents(fromRowId)
      .map { env =>
        env.event match {
          case evt: E     => evt
          case unexpected => sys.error(s"Oeps!! That's was totally unexpected $unexpected")
        }
      }

  def startCurrentSource[E](persistenceId: String, fromRowId: Long)(implicit tag: ClassTag[E]): Source[E, NotUsed] =
    PersistenceQuery(system)
      .readJournalFor[PostgresReadJournal](PostgresReadJournal.Identifier)
      .currentEventsByPersistenceId(persistenceId, fromRowId, Long.MaxValue)
      .map { env =>
        env.event match {
          case evt: E     => evt
          case unexpected => sys.error(s"Oeps!! That's was totally unexpected $unexpected")
        }
      }

}
