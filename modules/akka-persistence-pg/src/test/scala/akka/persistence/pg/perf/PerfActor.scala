package akka.persistence.pg.perf

import java.util.UUID

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import akka.persistence.pg.event.ReadModelUpdates
import slick.dbio.DBIO
import slick.jdbc.{PositionedResult, GetResult}

import scala.concurrent.{Promise, Await, Future}
import scala.concurrent.duration._
import scala.util.{Random, Try}

import scala.language.postfixOps

class PerfActor extends PersistentActor with ActorLogging {
  import PerfActor._

  override val persistenceId: String = "TestActor_"+UUID.randomUUID().toString

  override def receiveRecover: Receive = { case _ => }

  override def receiveCommand: Receive = {
    case Alter(txt) => persist(Altered(txt)) { _ => sender ! txt }
  }

}

object PerfActor {
  case class Alter(text: String)
  case class Altered(text: String)
  def props = Props(new PerfActor)
}

