package akka.persistence.pg.testkit

import akka.persistence.journal.JournalSpec
import akka.persistence.pg.event.{DefaultTagger, JsonEncoder, NoneJsonEncoder}
import akka.persistence.pg.journal.JournalStore
import akka.persistence.pg.util.RecreateSchema
import akka.persistence.pg.{PgExtension, PluginConfig}
import akka.serialization.{Serialization, SerializationExtension}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class PgAsyncJournalSpec extends JournalSpec with JournalStore with RecreateSchema {
  lazy val config = ConfigFactory.load("pg-application.conf")

  override val schemaName = config.getString("postgres.schema")
  override val pluginConfig = PluginConfig(system)
  override val serialization: Serialization = SerializationExtension(system)
  override val pgExtension: PgExtension = PgExtension(system)
  override val eventEncoder: JsonEncoder = NoneJsonEncoder
  override val eventTagger = DefaultTagger

  import akka.persistence.pg.PgPostgresDriver.api._

  override def beforeAll() {
    Await.result(pluginConfig.database.run(recreateSchema.andThen(journals.schema.create)), 10 seconds)
    super.beforeAll()
  }

}


