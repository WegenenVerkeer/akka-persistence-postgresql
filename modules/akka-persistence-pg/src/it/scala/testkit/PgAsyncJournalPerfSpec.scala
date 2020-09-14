package testkit

import akka.persistence.CapabilityFlag
import akka.persistence.journal.JournalPerfSpec
import akka.persistence.pg.util.{CreateTables, RecreateSchema}
import akka.persistence.pg.{PgConfig, PgExtension}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Second, Span}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class PgAsyncJournalPerfSpec extends JournalPerfSpec(ConfigFactory.load("pg-perf-spec.conf"))
with RecreateSchema
with ScalaFutures
with CreateTables
with PgConfig {

  val logger = LoggerFactory.getLogger(getClass)

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Second), interval = Span(100, Milliseconds))
  private val pgExtension: PgExtension = PgExtension(system)
  override lazy val pluginConfig = pgExtension.pluginConfig

  override def eventsCount = 5000
  override def awaitDurationMillis: Long = 30.seconds toMillis

  override def beforeAll():Unit = {
    database.run(recreateSchema
      .andThen(createTables)).futureValue
    super.beforeAll()
  }

  override def afterEach() = {
    pgExtension.whenDone(Future.successful(())).futureValue
    ()
  }

  override def afterAll() = {
    pgExtension.terminateWhenReady().futureValue
    ()
  }

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = false
}
