package akka.persistence.pg.testkit

import akka.persistence.journal.JournalSpec
import akka.persistence.pg.event.{NotTagged, DefaultTagger, JsonEncoder, NoneJsonEncoder}
import akka.persistence.pg.journal.{JournalTable, NotPartitioned, JournalStore}
import akka.persistence.pg.util.{CreateTables, RecreateSchema}
import akka.persistence.pg.{PgConfig, PgExtension, PluginConfig}
import akka.serialization.{Serialization, SerializationExtension}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Second, Span}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class PgAsyncJournalSpec extends JournalSpec(ConfigFactory.load("pg-application.conf"))
  with JournalTable
  with RecreateSchema
  with ScalaFutures
  with CreateTables
  with PgConfig {

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Second), interval = Span(100, Milliseconds))

  override val pluginConfig = PluginConfig(system)

  import driver.api._

  override def beforeAll() {
    pluginConfig.database.run(recreateSchema
      .andThen(journals.schema.create)
      .andThen(createRowIdSequence)).futureValue
    super.beforeAll()
  }

}


