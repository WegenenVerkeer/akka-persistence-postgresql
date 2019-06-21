package akka.persistence.pg.testkit

import akka.persistence.CapabilityFlag
import akka.persistence.journal.JournalSpec
import akka.persistence.pg.journal.JournalTable
import akka.persistence.pg.util.{CreateTables, RecreateSchema}
import akka.persistence.pg.{PgConfig, PgExtension}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Second, Span}

class PgAsyncJournalSpec
    extends JournalSpec(ConfigFactory.load("pg-application.conf"))
    with JournalTable
    with RecreateSchema
    with ScalaFutures
    with CreateTables
    with PgConfig {

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Second), interval = Span(100, Milliseconds))

  override lazy val pluginConfig = PgExtension(system).pluginConfig

  import driver.api._

  override def beforeAll() {
    pluginConfig.database
      .run(
        recreateSchema
          .andThen(journals.schema.create)
      )
      .futureValue
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    system.terminate()
    system.whenTerminated.futureValue
    ()
  }

  override protected def supportsRejectingNonSerializableObjects: CapabilityFlag = false

  protected override def supportsSerialization: CapabilityFlag = false
}
