package akka.persistence.pg.testkit

import akka.persistence.pg.snapshot.SnapshotTable
import akka.persistence.pg.{PgConfig, PgExtension, PluginConfig}
import akka.persistence.pg.util.RecreateSchema
import akka.persistence.snapshot.SnapshotStoreSpec
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Second, Span}

class PgSnapshotStoreSpec
    extends SnapshotStoreSpec(ConfigFactory.load("pg-application.conf"))
    with SnapshotTable
    with RecreateSchema
    with ScalaFutures
    with PgConfig {

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Second), interval = Span(100, Milliseconds))

  override lazy val pluginConfig: PluginConfig = PgExtension(system).pluginConfig

  import driver.api._

  override def beforeAll() {
    database.run(recreateSchema.andThen(snapshots.schema.create)).futureValue
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    system.terminate()
    system.whenTerminated.futureValue
    ()
  }

}
