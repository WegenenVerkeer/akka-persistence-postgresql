package akka.persistence.pg.testkit

import akka.actor.ActorLogging
import akka.persistence.pg.journal.NotPartitioned
import akka.persistence.pg.snapshot.PgSnapshotStore
import akka.persistence.pg.PluginConfig
import akka.persistence.pg.util.RecreateSchema
import akka.persistence.snapshot.SnapshotStoreSpec
import akka.serialization.{Serialization, SerializationExtension}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class PgSnapshotStoreSpec extends SnapshotStoreSpec
  with PgSnapshotStore
  with RecreateSchema {

  lazy val config = ConfigFactory.load("pg-application.conf")

  override val schemaName = config.getString("postgres.schema")
  override val pluginConfig = PluginConfig(system)
  override val serialization: Serialization = SerializationExtension(system)
  override val partitioner = NotPartitioned

  import akka.persistence.pg.PgPostgresDriver.api._

  override def beforeAll() {
    Await.result(pluginConfig.database.run(recreateSchema.andThen(snapshots.schema.create)), 10 seconds)
    super.beforeAll()
  }

}


