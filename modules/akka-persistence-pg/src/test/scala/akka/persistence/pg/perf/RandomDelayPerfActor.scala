package akka.persistence.pg.perf

import java.util.UUID

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import akka.persistence.pg.PgPostgresProfile
import akka.persistence.pg.event.{EventWrapper, ExtraDBIOSupport}
import akka.persistence.pg.perf.Messages.{Alter, Altered}
import slick.jdbc.{GetResult, PositionedResult}

import scala.util.Random

object RandomDelayPerfActor {
  def props(driver: PgPostgresProfile, persistenceId: Option[String] = None) =
    Props(new RandomDelayPerfActor(driver, persistenceId))
}

class RandomDelayPerfActor(driver: PgPostgresProfile, pid: Option[String]) extends PersistentActor with ActorLogging {

  override val persistenceId: String = pid.getOrElse("TestActor_" + UUID.randomUUID().toString)

  override def receiveRecover: Receive = { case _ => }

  override def receiveCommand: Receive = {
    case Alter(txt) =>
      persist(new ExtraDBIOSupport with EventWrapper[Altered] {
        import driver.api._
        implicit object GetUnit extends GetResult[Unit] { def apply(rs: PositionedResult) = { rs.nextObject(); () } }

        override def extraDBIO: DBIO[_] = sql"""select pg_sleep(${Random.nextInt(150) / 1000})""".as[Unit]

        override def failureHandler = PartialFunction.empty

        override def event: Altered = Altered(txt, System.currentTimeMillis())
      }) { _ =>
        sender ! txt
      }
  }

}
