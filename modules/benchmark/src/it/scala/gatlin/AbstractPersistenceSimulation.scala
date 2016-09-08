package gatlin

import akka.actor.ActorSystem
import akka.persistence.pg.perf.Messages.Alter
import akka.persistence.pg.util.{CreateTables, RecreateSchema}
import akka.persistence.pg.{PgConfig, PluginConfig}
import com.typesafe.config.Config
import io.gatling.core.scenario.Simulation
import io.gatling.core.session.{Session => GSession, Expression}
import io.gatling.commons.validation.{Success, Validation}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

abstract class AbstractPersistenceSimulation(val config: Config)
  extends Simulation
  with PgConfig
  with RecreateSchema
  with CreateTables
{

  override val pluginConfig = PluginConfig(config)
  val system = ActorSystem("benchmark-system", config)

  /**
   * recreate schema and tables + indices before running the benchmark
   */
  before {
    Await.result(database.run(
      recreateSchema.andThen(createTables).andThen(createEventIndex)
    ), 10 seconds)
    warmup()
  }

  def warmup(): Unit = {}

  val feeder = Iterator.continually(
    Map("text" -> Random.alphanumeric.take(32).mkString)
  )

  case class AlterMessage(text: Expression[String]) extends AskMessage {
    override def apply(session: GSession): Validation[Any] = {
      text(session).flatMap { s => Success(Alter(s)) }
    }
  }

}
