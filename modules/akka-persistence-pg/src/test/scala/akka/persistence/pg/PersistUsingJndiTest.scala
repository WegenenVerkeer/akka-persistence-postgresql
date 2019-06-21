package akka.persistence.pg

import java.util.concurrent.TimeUnit
import javax.naming.{Context, InitialContext}

import akka.actor._
import akka.persistence.pg.TestActor.{Alter, GetState, TheState}
import akka.persistence.pg.journal.JournalTable
import akka.persistence.pg.util.{CreateTables, RecreateSchema}
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import org.postgresql.ds.PGSimpleDataSource
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Seconds, Span}

class PersistUsingJndiTest
    extends FunSuite
    with Matchers
    with BeforeAndAfterAll
    with JournalTable
    with CreateTables
    with RecreateSchema
    with PgConfig
    with WaitForEvents
    with ScalaFutures {

  override implicit val patienceConfig = PatienceConfig(timeout = Span(3, Seconds), interval = Span(100, Milliseconds))

  val config: Config                           = ConfigFactory.load("pg-persist-jndi.conf")
  implicit val system                          = ActorSystem("TestCluster", config)
  override lazy val pluginConfig: PluginConfig = PgExtension(system).pluginConfig

  import driver.api._

  val testProbe        = TestProbe()
  implicit val timeOut = Timeout(1, TimeUnit.MINUTES)

  test("generate events") {
    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    testProbe.send(test, Alter("foo"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "foo"))

    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "bar"))

    database.run(journals.size.result).futureValue shouldBe 2
  }

  override def beforeAll() {
    System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "tyrex.naming.MemoryContextFactory")
    System.setProperty(Context.PROVIDER_URL, "/")

    val simpleDataSource = new PGSimpleDataSource()
    simpleDataSource.setUrl(pluginConfig.dbConfig.getString("url"))
    simpleDataSource.setUser(pluginConfig.dbConfig.getString("user"))
    simpleDataSource.setPassword(pluginConfig.dbConfig.getString("password"))
    simpleDataSource.setPrepareThreshold(1)

    new InitialContext().rebind("MyDS", simpleDataSource)

    database.run(recreateSchema.andThen(createTables)).futureValue
    ()
  }

  override protected def afterAll(): Unit = {
    system.terminate()
    system.whenTerminated.futureValue
    ()
  }

}
