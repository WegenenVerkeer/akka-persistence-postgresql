package akka.persistence.pg.perf

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.Props
import akka.persistence.PersistentActor
import akka.persistence.pg.event.{EventWrapper, ReadModelUpdate}
import akka.persistence.pg.PgPostgresDriver

object WithReadModelUpdateActor {
  private val id = new AtomicInteger(0)

  def props(driver: PgPostgresDriver, fullTableName: String) = Props(new WithReadModelUpdateActor(driver, fullTableName, id.incrementAndGet()))
}


class WithReadModelUpdateActor(driver: PgPostgresDriver, fullTableName: String, id: Int) extends PersistentActor {

  import PerfActor._

  override val persistenceId: String = s"TestActor_$id"

  override def receiveRecover: Receive = { case _ => }

  override def receiveCommand: Receive = {
    case Alter(txt) => persist(new ReadModelUpdate with EventWrapper[Altered] {

      import driver.api._
      override def readModelAction: DBIO[_] = sqlu"""update #$fullTableName set txt = $txt where id = $id"""

      override def event: Altered = Altered(txt)
    }
    ) { _ => sender ! txt }
  }


}
