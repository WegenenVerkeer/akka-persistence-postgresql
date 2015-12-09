package gatlin

import akka.actor.ActorRef
import akka.pattern.ask
import akka.persistence.pg.perf.Messages.Alter
import akka.persistence.pg.perf.PerfActor
import akka.util.Timeout
import com.typesafe.config.Config
import gatlin.Predef._
import io.gatling.core.Predef._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

abstract class MultiActorPerfSimulation(override val config: Config) extends AbstractPersistenceSimulation(config)
{

  val numActors = 100
  var actors: Seq[ActorRef] = _

  override def warmup() = {
    actors = 1 to numActors map { _ => system.actorOf(PerfActor.props) }
    implicit val timeout = Timeout(2 seconds)
    import scala.concurrent.ExecutionContext.Implicits.global
    val f = actors map { _ ? Alter("warmup") }
    Await.result(Future sequence f, 10 seconds)
    ()
  }

  val scn = scenario("multiple persistent actors").during(30 seconds) {
    feed(feeder)
    .exec { session => session.set("actor", actors(Random.nextInt(numActors))) }
    .exec { request(AlterMessage("${text}")) }
  }

  setUp(scn.inject(atOnceUsers(100)))

}
