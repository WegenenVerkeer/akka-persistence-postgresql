package gatlin

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.util.Timeout
import io.gatling.core.action.{Chainable, Action}
import io.gatling.core.result.message._
import io.gatling.core.result.writer.DataWriterClient
import io.gatling.core.session.Session
import io.gatling.core.util.TimeHelper.nowMillis

import scala.util.control.NonFatal

object AskAction {

  implicit val timeout = Timeout(1, TimeUnit.MINUTES)

}

class AskAction(val next: ActorRef,
                val message: AskMessage,
                requestName: String)
  extends Action
  with DataWriterClient
  with Chainable {

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
        writeRequestData(session,
          "Request " + requestName,
          start,
          stop,
          start,
          stop,
          status)
        next ! (if (status == OK) session.markAsSucceeded else session.markAsFailed)
    }
  }
}
