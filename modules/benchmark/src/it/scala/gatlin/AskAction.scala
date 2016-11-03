package gatlin

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import io.gatling.core.action.{Action, ChainableAction}
import io.gatling.commons.stats._
import io.gatling.core.session.Session
import io.gatling.commons.util.TimeHelper.nowMillis
import io.gatling.core.stats.StatsEngine
import io.gatling.core.stats.message.ResponseTimings
import io.gatling.core.util.NameGen

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

object AskAction {

  implicit val timeout = Timeout(1, TimeUnit.MINUTES)

}

class AskAction(val next: Action,
                val message: AskMessage,
                val statsEngine: StatsEngine,
                implicit val executionContext: ExecutionContext)
  extends ChainableAction
  with NameGen {

  override val name = genName("ask")

  import AskAction.timeout

  override def execute(session: Session): Unit = {
    val sendTo = session("actor").as[ActorRef]
    val sendMessage = message(session).get

    val start = nowMillis
    (sendTo ? sendMessage)
      .map { _ => OK }
      .recover { case NonFatal(t) => KO }
      .foreach { status =>
        val stop = nowMillis
        statsEngine.logResponse(session, "Request", ResponseTimings(start, stop), status, None, None)
        next ! (if (status == OK) session.markAsSucceeded else session.markAsFailed)
    }
  }
}
