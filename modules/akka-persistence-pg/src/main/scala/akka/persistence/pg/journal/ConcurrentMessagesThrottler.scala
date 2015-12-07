package akka.persistence.pg.journal

import akka.actor._
import akka.dispatch.{DequeBasedMessageQueueSemantics, RequiresMessageQueue, BoundedDequeBasedMessageQueueSemantics, BoundedMessageQueueSemantics}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

trait ConcurrentMessagesThrottler {

  def getConcurrentMessages: Int

  def throttled[T](f: => Future[T])
                  (implicit executionContext: ExecutionContext): Future[T]

}

object NotThrottled extends ConcurrentMessagesThrottler {

  def throttled[T](f: => Future[T])
                  (implicit executionContext: ExecutionContext): Future[T] = f

  override def getConcurrentMessages: Int = Int.MaxValue
}

class ConcurrentMessagesThrottlerImpl(val maxOutstandingMessages: Int,
                                      val system: ActorSystem,
                                      implicit val timeout: Timeout) extends ConcurrentMessagesThrottler {

  private val throttlingActor = system.actorOf(Props(new ThrottlingActor()).withMailbox("akka.persistence.pg.throttler-mailbox"))

  def throttled[T](f: => Future[T])
                  (implicit executionContext: ExecutionContext): Future[T] = {
    val future = (throttlingActor ? RequestToken) flatMap {
      case TokenGranted => f
      case Status.Failure(t) => Future.failed(t)
    }
    future.onComplete(_ => throttlingActor ! ReturnToken)
    future
  }

  case object RequestToken
  case object TokenGranted
  case object ReturnToken

  class ThrottlingActor
    extends Actor
    with UnrestrictedStash
      with RequiresMessageQueue[BoundedDequeBasedMessageQueueSemantics] {

    var outstandingTokens = 0

    def checkoutToken() = {
      outstandingTokens += 1
      if (outstandingTokens == maxOutstandingMessages) {
        context.become(waiting)
      }
    }

    def handinToken() = {
      outstandingTokens -= 1
      context.become(receive)
      unstashAll()
    }

    override def receive: Receive = handleResults orElse {
      case RequestToken =>
        checkoutToken()
        sender ! TokenGranted
    }

    def handleResults: Receive = {
      case ReturnToken => handinToken()

    }

    def waiting: Receive = handleResults orElse {
      case _ => stash
    }

  }

  override def getConcurrentMessages: Int = maxOutstandingMessages
}
