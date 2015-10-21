package akka.persistence.pg.perf

import java.util.UUID

import akka.actor.{Props, ActorLogging}
import akka.persistence.PersistentActor
import akka.persistence.pg.PgPostgresDriver
import akka.persistence.pg.event.{Tagged, ReadModelUpdates}
import slick.jdbc.{GetResult, PositionedResult}

import scala.language.postfixOps
import scala.util.Random

object RandomDelayPerfActor {
  def props(driver: PgPostgresDriver) = Props(new RandomDelayPerfActor(driver))
}

class RandomDelayPerfActor(driver: PgPostgresDriver) extends PersistentActor with ActorLogging {
  import PerfActor._

  override val persistenceId: String = "TestActor_"+UUID.randomUUID().toString

  override def receiveRecover: Receive = { case _ => }

  override def receiveCommand: Receive = {
    case Alter(txt) => persist(new ReadModelUpdates with Tagged[Altered] {
      import driver.api._
      implicit object GetUnit extends GetResult[Unit] { def apply(rs: PositionedResult) = { rs.nextObject(); () } }

      override def readModelUpdates: Seq[DBIO[_]] = Seq(sql"""select pg_sleep(${Random.nextInt(150)/1000})""".as[Unit])

      override def tags = Map.empty

      override def event: Altered = Altered(txt)
    }
    ) { _ => sender ! txt }
  }



}



