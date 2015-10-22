package gatlin

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.persistence.pg.perf.PerfActor
import PerfActor.Alter
import akka.persistence.pg.util.RecreateSchema
import akka.persistence.pg.{PgConfig, PluginConfig}
import akka.util.Timeout
import com.typesafe.config.Config
import gatlin.Predef._
import io.gatling.core.Predef._
import io.gatling.core.scenario.Simulation
import io.gatling.core.session.Expression
import io.gatling.core.validation.Validation

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

abstract class SingleActorPerfSimulation(override val config: Config) extends AbstractPersistenceSimulation(config)
{

  var actor: ActorRef = _

  override def warmup() = {
    actor = system.actorOf(PerfActor.props)
    implicit val timeout = Timeout(2 seconds)
    Await.result(actor ? Alter("warmup"), 10 seconds)
    ()
  }

  val scn = scenario("single persistent actor").during(30 seconds) {
    feed(feeder)
      .exec { session => session.set("actor", actor) }
      .exec { request(AlterMessage("${text}")) }
  }

  setUp(scn.inject(atOnceUsers(10)))

}
