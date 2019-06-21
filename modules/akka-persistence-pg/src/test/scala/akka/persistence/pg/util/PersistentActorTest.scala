package akka.persistence.pg.util

import java.sql.Savepoint
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.Config
import org.scalatest._
import slick.jdbc.JdbcBackend

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

/**
  * Base class for testing a persistent actor
  * db sessions are rolled back after each test, maintaining a clean db state
  * This also means the actorsystem needs to be recreated for each test
  */
trait PersistentActorTest extends fixture.FunSuiteLike with BeforeAndAfterEach {

  def config: Config

  implicit val defaultTimeout = Timeout(10, TimeUnit.SECONDS)

  implicit var system: ActorSystem = _
  var testProbe: TestProbe         = _

  override protected def beforeEach(): Unit = {
    system = ActorSystem("PersistentActorTest", config)
    testProbe = TestProbe()
  }

  type FixtureParam = JdbcBackend.DatabaseDef

  override protected def withFixture(test: OneArgTest): Outcome = {
    val possibleOutcome = Try {
      PgPluginTestUtil.withTransactionRollback { db =>
        withFixture(test.toNoArgTest(db))
      }
    }
    //akka shutdown must be done in this way instead of using afterEach
    system.terminate()
    Await.result(system.whenTerminated, Duration.Inf)
    possibleOutcome.get
  }

  def savepoint()(implicit db: JdbcBackend.DatabaseDef): Savepoint         = db.createSession().conn.setSavepoint()
  def rollback(savepoint: Savepoint)(implicit db: JdbcBackend.DatabaseDef) = db.createSession().conn.rollback(savepoint)

}
