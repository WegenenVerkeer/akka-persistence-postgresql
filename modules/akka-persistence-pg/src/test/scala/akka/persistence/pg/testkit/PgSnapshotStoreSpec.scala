package akka.persistence.pg.testkit

import akka.persistence.pg.journal.NotPartitioned
import akka.persistence.pg.snapshot.PgSnapshotStore
import akka.persistence.pg.{PgConfig, PluginConfig}
import akka.persistence.pg.util.RecreateSchema
import akka.persistence.snapshot.SnapshotStoreSpec
import akka.serialization.{Serialization, SerializationExtension}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Second, Span}

import scala.language.postfixOps

class PgSnapshotStoreSpec extends SnapshotStoreSpec(ConfigFactory.load("pg-application.conf"))
  with PgSnapshotStore
  with RecreateSchema
  with ScalaFutures
  with PgConfig {

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Second), interval = Span(100, Milliseconds))

  override val pluginConfig = PluginConfig(system)
  override val serialization: Serialization = SerializationExtension(system)
  override val partitioner = NotPartitioned

  import driver.api._

  override def beforeAll() {
    pluginConfig.database.run(recreateSchema.andThen(snapshots.schema.create)).futureValue
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    system.terminate()
    system.whenTerminated.futureValue
    ()
  }


}


