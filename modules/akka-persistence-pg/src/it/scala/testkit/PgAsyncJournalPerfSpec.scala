package testkit

import akka.pattern.ask
import akka.persistence.journal.JournalPerfSpec
import akka.persistence.pg.journal.RowIdUpdater.IsBusy
import akka.persistence.pg.{PgExtension, PluginConfig, PgConfig}
import akka.persistence.pg.util.{CreateTables, RecreateSchema}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Second, Span}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.language.postfixOps

class PgAsyncJournalPerfSpec extends JournalPerfSpec(ConfigFactory.load("pg-perf-spec.conf"))
with RecreateSchema
with ScalaFutures
with CreateTables
with PgConfig {

  val logger = LoggerFactory.getLogger(getClass)

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Second), interval = Span(100, Milliseconds))
  override lazy val pluginConfig = PgExtension(system).pluginConfig

  override def eventsCount = 5000
  override def awaitDurationMillis: Long = 30.seconds toMillis

  override def beforeAll() {
    database.run(recreateSchema
      .andThen(createTables)).futureValue
    super.beforeAll()
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  override def afterEach() = {
    logger.info("wait for RowIdUpdater to finish")
    implicit val timeout = Timeout(20 seconds)

    def isBusy() = {
      system.actorSelection("/user/AkkaPgRowIdUpdater")
        .resolveOne()
        .flatMap { rowIdUpdater =>
          rowIdUpdater ? IsBusy }
        .mapTo[Boolean]
        .futureValue
    }

    while (isBusy()) {
      logger.info("still busy")
      Thread.sleep(1000)
    }
    logger.info("RowIdUpdater is finished")
  }

  override def afterAll() = {
    system.terminate()
    system.whenTerminated.futureValue
    ()
  }
}
