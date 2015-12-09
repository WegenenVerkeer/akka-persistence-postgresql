package gatlin

import akka.actor.ActorRef
import akka.pattern.ask
import akka.persistence.pg.perf.Messages.Alter
import akka.persistence.pg.perf.PerfActor
import akka.util.Timeout
import com.typesafe.config.Config
import gatlin.Predef._
import io.gatling.core.Predef._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

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
