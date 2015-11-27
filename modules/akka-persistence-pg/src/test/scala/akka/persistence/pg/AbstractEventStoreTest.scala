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

import scala.concurrent.Await
import scala.concurrent.duration._

import scala.language.postfixOps

abstract class AbstractEventStoreTest
  extends TestKit(ActorSystem("TestCluster", AbstractEventStoreTest.config))
  with FunSuiteLike with BeforeAndAfterEach with ShouldMatchers
  with BeforeAndAfterAll with JournalStore with EventStore with RecreateSchema
  with PgSnapshotStore
  with PgConfig
  with ScalaFutures {

  override val schemaName = AbstractEventStoreTest.config.getString("postgres.schema")

  override val serialization: Serialization = SerializationExtension(system)
  override val pgExtension: PgExtension = PgExtension(system)
  override val pluginConfig = PluginConfig(system)
  override val eventEncoder = new TestEventEncoder()
  override val eventTagger = new TestEventTagger
  override val partitioner = NotPartitioned

  val testProbe = TestProbe()

  import driver.api._

  override def beforeAll() {
    Await.result(database.run(
      recreateSchema
        .andThen((journals.schema ++ snapshots.schema).create)
        .andThen( sqlu"""create sequence #${pluginConfig.fullRowIdSequenceName}""")
    ), 10 seconds)
    super.beforeAll()
  }

  override protected def beforeEach(): Unit = {
    Await.result(database.run(DBIO.seq(
      journals.delete,
      snapshots.delete
    )), 10 seconds)
    super.beforeEach()
  }

  override protected def afterAll(): Unit = {
    Await.ready(system.terminate(), 3.seconds)
    ()
  }
}

object AbstractEventStoreTest {
  val config = ConfigFactory.load("pg-eventstore.conf")
}