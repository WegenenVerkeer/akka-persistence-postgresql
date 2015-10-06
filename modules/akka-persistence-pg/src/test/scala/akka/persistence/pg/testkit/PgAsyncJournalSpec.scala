package akka.persistence.pg.testkit

import akka.persistence.journal.JournalSpec
import akka.persistence.pg.event.{DefaultTagger, JsonEncoder, NoneJsonEncoder}
import akka.persistence.pg.journal.{NotPartitioned, JournalStore}
import akka.persistence.pg.util.RecreateSchema
import akka.persistence.pg.{PgConfig, PgExtension, PluginConfig}
import akka.serialization.{Serialization, SerializationExtension}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class PgAsyncJournalSpec extends JournalSpec(ConfigFactory.load("pg-application.conf"))
  with JournalStore
  with RecreateSchema
  with PgConfig {

  override val schemaName = config.getString("postgres.schema")
  override val pluginConfig = PluginConfig(system)
  override val serialization: Serialization = SerializationExtension(system)
  override val pgExtension: PgExtension = PgExtension(system)
  override val eventEncoder: JsonEncoder = NoneJsonEncoder
  override val eventTagger = DefaultTagger
  override val partitioner = NotPartitioned

  import driver.api._

  override def beforeAll() {
    Await.result(pluginConfig.database.run(recreateSchema
      .andThen(journals.schema.create)
      .andThen(sqlu"""create sequence #${pluginConfig.fullRowIdSequenceName}""")), 10 seconds)
    super.beforeAll()
  }

}


