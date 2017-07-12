package testkit

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.persistence.journal.JournalPerfSpec.{BenchActor, Cmd}
import akka.persistence.pg.util.{CreateTables, RecreateSchema}
import akka.persistence.pg.{PgConfig, PgExtension}
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSuite}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Second, Span}
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class PgAsyncJournalPerfSuite extends FunSuite
with RecreateSchema
with ScalaFutures
with CreateTables
with BeforeAndAfterEach
with BeforeAndAfterAll
with PgConfig {

  val logger = LoggerFactory.getLogger(getClass)

  lazy val config = ConfigFactory.load("pg-perf-spec.conf")
  implicit val system = ActorSystem("PgAsyncJournalPerfSuite", config)

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Second), interval = Span(100, Milliseconds))
  private val pgExtension: PgExtension = PgExtension(system)
  override lazy val pluginConfig = pgExtension.pluginConfig

  override def beforeAll() {
    database.run(recreateSchema
      .andThen(createTables)).futureValue
    super.beforeAll()
  }

  override def afterEach() = {
    logger.info("wait for RowIdUpdater to finish")
    pgExtension.whenDone(Future.successful(())).futureValue
    logger.info("RowIdUpdater is finished")
  }

  override def afterAll() = {
    pgExtension.terminateWhenReady().futureValue
    ()
  }

  private val testProbe = TestProbe()

  def benchActor(pid: String, replyAfter: Int): ActorRef =
    system.actorOf(Props(classOf[BenchActor], pid, testProbe.ref, replyAfter))

  def feedAndExpectLast(actor: ActorRef, mode: String, cmnds: immutable.Seq[Int]): Unit = {
    cmnds foreach { c ⇒ actor ! Cmd(mode, c) }
    testProbe.expectMsg(awaitDuration, cmnds.last)
  }

  /** Executes a block of code multiple times (no warm-up) */
  def measure(msg: Duration ⇒ String)(block: ⇒ Unit): Unit = {
    val measurements = Array.ofDim[Duration](measurementIterations)
    var i = 0
    while (i < measurementIterations) {
      val start = System.nanoTime()

      block

      val stop = System.nanoTime()
      val d = (stop - start).nanos
      measurements(i) = d
      info(msg(d))

      i += 1
    }
    info(s"Average time: ${(measurements.map(_.toNanos).sum / measurementIterations).nanos.toMillis} ms")
  }

  /** Override in order to customize timeouts used for expectMsg, in order to tune the awaits to your journal's perf */
  def awaitDurationMillis: Long = 100.seconds.toMillis

  /** Override in order to customize timeouts used for expectMsg, in order to tune the awaits to your journal's perf */
  private def awaitDuration: FiniteDuration = awaitDurationMillis.millis

  /** Number of messages sent to the PersistentActor under test for each test iteration */
  def eventsCount: Int = 10 * 2000

  /** Number of measurement iterations each test will be run. */
  def measurementIterations: Int = 50

  private val commands = Vector(1 to eventsCount: _*)

  test(s"recovering $eventsCount events") {
    val pid: String = UUID.randomUUID().toString
    val p1 = benchActor(pid, eventsCount)
    feedAndExpectLast(p1, "p", commands)

    measure(d ⇒ s"Recovering $eventsCount took ${d.toMillis} ms") {
      benchActor(pid, eventsCount)
      testProbe.expectMsg(max = awaitDuration, commands.last)
    }
  }

}
