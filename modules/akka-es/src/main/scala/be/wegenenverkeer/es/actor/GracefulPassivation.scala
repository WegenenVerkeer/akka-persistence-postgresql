package be.wegenenverkeer.es.actor

import akka.actor.{ActorLogging, Actor, PoisonPill, ReceiveTimeout}

import scala.concurrent.duration._

case class Passivate(stopMessage: Any)

case class PassivationConfig(passivationMsg: Any = PoisonPill, inactivityTimeout: Duration = 10.minutes)

trait GracefulPassivation extends Actor with ActorLogging {

  val pc: PassivationConfig = PassivationConfig()

  override def preStart() {
    context.setReceiveTimeout(pc.inactivityTimeout)
  }

  override def unhandled(message: Any) {
    message match {
      case ReceiveTimeout =>
        log.debug("ReceiveTimeout => sending Passivate message to {}", context.parent)
        context.parent ! Passivate(pc.passivationMsg)
      case _ => super.unhandled(message)
    }
  }

}
