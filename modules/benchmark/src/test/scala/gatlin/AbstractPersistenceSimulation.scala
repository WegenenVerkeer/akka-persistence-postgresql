package gatlin

import akka.actor.ActorSystem
import akka.persistence.pg.perf.PerfActor
import PerfActor.Alter
import akka.persistence.pg.util.RecreateSchema
import akka.persistence.pg.{PgConfig, PluginConfig}
import com.typesafe.config.Config
import io.gatling.core.Predef._
import io.gatling.core.scenario.Simulation
import io.gatling.core.session.{Session => GSession, Expression}
import io.gatling.core.validation.Validation
import org.scalatest.concurrent.ScalaFutures

import scala.language.postfixOps
import scala.util.Random

abstract class AbstractPersistenceSimulation(val config: Config)
  extends Simulation
  with PgConfig
  with RecreateSchema
  with ScalaFutures
{

  override val pluginConfig = PluginConfig(config)
  val system = ActorSystem("benchmark-system", config)
  val schemaName = config.getString("postgres.schema")

  import driver.api._

  val createJournal = sqlu"""create table "#$schemaName".journal (
                           "id" BIGSERIAL NOT NULL PRIMARY KEY,
                           "rowid" BIGINT DEFAULT NULL,
                           "persistenceid" VARCHAR(254) NOT NULL,
                           "sequencenr" INT NOT NULL,
                           "partitionkey" VARCHAR(254) DEFAULT NULL,
                           "deleted" BOOLEAN DEFAULT false,
                           "sender" VARCHAR(512),
                           "payload" BYTEA,
                           "payloadmf" VARCHAR(512),
                           "uuid" VARCHAR(254) NOT NULL,
                           "created" timestamptz NOT NULL,
                           "tags" HSTORE,
                           "event" JSONB,
                           constraint "cc_journal_payload_event" check (payload IS NOT NULL OR event IS NOT NULL))"""

  val createUniqueIndex = sqlu"""CREATE INDEX journal_pidseq_idx ON "#$schemaName".journal (persistenceid, sequencenr)"""
  val createEventIndex = sqlu"""CREATE INDEX journal_event_idx ON "#$schemaName".journal USING gin (event)"""
  val createRowIdIndex = sqlu"""CREATE INDEX journal_rowid_idx ON "#$schemaName".journal (rowid)"""
  val createRowIdSequence = sqlu"""create sequence #${pluginConfig.fullRowIdSequenceName}"""

  /**
   * recreate schema and tables + indices before running the benchmark
   */
  before {
    database.run(
      recreateSchema
        .andThen(createJournal)
        .andThen(createUniqueIndex)
        .andThen(createEventIndex)
        .andThen(createRowIdIndex)
        .andThen(createRowIdSequence)
    ).futureValue
    warmup()
  }

  def warmup(): Unit = {}

  val feeder = Iterator.continually(
    Map("text" -> Random.alphanumeric.take(32).mkString)
  )

  case class AlterMessage(text: Expression[String]) extends AskMessage {
    override def apply(session: GSession): Validation[Any] = {
      text(session).flatMap { s => Alter(s) }
    }
  }

}
