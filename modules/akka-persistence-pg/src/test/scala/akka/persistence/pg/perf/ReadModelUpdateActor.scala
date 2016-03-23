package akka.persistence.pg.perf

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.Props
import akka.persistence.PersistentActor
import akka.persistence.pg.event.{EventWrapper, ReadModelUpdate}
import akka.persistence.pg.PgPostgresDriver
import akka.persistence.pg.perf.Messages.{Altered, Alter}
import akka.persistence.pg.perf.ReadModelUpdateActor.TextNotUnique
import org.postgresql.util.PSQLException
import slick.jdbc.{PositionedResult, GetResult}

object ReadModelUpdateActor {
  case object TextNotUnique

  private val id = new AtomicInteger(0)
  def reset() = id.set(0)

  def props(driver: PgPostgresDriver, fullTableName: String) = Props(new ReadModelUpdateActor(driver, fullTableName, id.incrementAndGet()))
}


class ReadModelUpdateActor(driver: PgPostgresDriver, fullTableName: String, id: Int) extends PersistentActor {

  override val persistenceId: String = s"TestActor_$id"

  override def receiveRecover: Receive = { case _ => }

  override def receiveCommand: Receive = {
    case Alter(txt) => persist(new ReadModelUpdate with EventWrapper[Altered] {

      import driver.api._
      import context.dispatcher
      implicit object GetUnit extends GetResult[Unit] { def apply(rs: PositionedResult) = { rs.nextObject(); () } }

      override def readModelAction: DBIO[_] =
        sql"""select cnt from #$fullTableName where id = $id""".as[Long]
          .flatMap { c =>
            val i = c(0).toInt + 1
            sqlu"""update #$fullTableName set txt = $txt, cnt=$i where id = $id"""
          }

      override def failureHandler = { case t: PSQLException if t.getSQLState == "23505" => sender ! TextNotUnique }

      override def event: Altered = Altered(txt, System.currentTimeMillis())
    }
    ) { _ => sender ! txt }
  }

  override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
    event match {
      case readModelUpdate: ReadModelUpdate =>
        if (readModelUpdate.failureHandler.isDefinedAt(cause)) {
          readModelUpdate.failureHandler(cause)
        }
      case _ =>
    }


  }
}
