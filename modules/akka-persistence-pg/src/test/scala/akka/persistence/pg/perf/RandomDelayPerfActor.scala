package akka.persistence.pg.perf

import java.util.UUID

import akka.actor.{Props, ActorLogging}
import akka.persistence.PersistentActor
import akka.persistence.pg.PgPostgresDriver
import akka.persistence.pg.event.{EventWrapper, ReadModelUpdate}
import akka.persistence.pg.perf.Messages.{Altered, Alter}
import slick.jdbc.{GetResult, PositionedResult}

import scala.language.postfixOps
import scala.util.Random

object RandomDelayPerfActor {
  def props(driver: PgPostgresDriver) = Props(new RandomDelayPerfActor(driver))
}

class RandomDelayPerfActor(driver: PgPostgresDriver) extends PersistentActor with ActorLogging {

  override val persistenceId: String = "TestActor_"+UUID.randomUUID().toString

  override def receiveRecover: Receive = { case _ => }

  override def receiveCommand: Receive = {
    case Alter(txt) => persist(new ReadModelUpdate with EventWrapper[Altered] {
      import driver.api._
      implicit object GetUnit extends GetResult[Unit] { def apply(rs: PositionedResult) = { rs.nextObject(); () } }

      override def readModelAction: DBIO[_] = sql"""select pg_sleep(${Random.nextInt(150)/1000})""".as[Unit]

      override def failureHandler = PartialFunction.empty

      override def event: Altered = Altered(txt)
    }
    ) { _ => sender ! txt }
  }



}



