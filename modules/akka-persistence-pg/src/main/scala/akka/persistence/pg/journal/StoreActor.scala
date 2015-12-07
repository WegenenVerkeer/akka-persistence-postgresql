package akka.persistence.pg.journal

import akka.actor._
import akka.pattern.pipe
import akka.persistence.pg.PgPostgresDriver
import akka.persistence.pg.journal.StoreActor.{Store, StoreSuccess}
import slick.jdbc.JdbcBackend

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

private object StoreActor {

  def props(driver: PgPostgresDriver,
            database: WriteStrategy#DbLike) = Props(new StoreActor(driver, database))

  import slick.dbio.DBIO

  case class Store(actions: Seq[DBIO[_]])
  case object StoreSuccess
}

private class StoreActor(driver: PgPostgresDriver,
                         database: WriteStrategy#DbLike)
  extends Actor
  with ActorLogging {

  case class Done(senders: List[ActorRef], result: Try[Unit])
  case object Run

  import context.dispatcher
  import driver.api._

  private var senders: List[ActorRef] = List.empty[ActorRef]
  private var actions: Seq[driver.api.DBIO[_]] = Seq.empty[DBIO[_]]

  override def receive: Receive = idle

  def idle: Receive = {
    case Store(actions) =>
      this.actions ++= actions
      this.senders :+= sender()
      self ! Run
    case Run =>
      if (senders.nonEmpty) {
        val _senders = senders
        database.run(DBIO.seq(this.actions: _*).transactionally)
          .map { _ => Done(_senders, Success(())) }
          .recover { case NonFatal(t) => Done(_senders, Failure(t)) }
          .pipeTo(self)
        this.actions = Seq.empty
        this.senders = List.empty
        context become busy
      }
  }

  def busy: Receive = {
    case Done(senders, r) =>
      r match {
        case Success(_) => senders foreach { _ ! StoreSuccess }
        case Failure(t)  => senders foreach { _ ! Status.Failure(t) }
      }
      context become idle
      self ! Run
    case Store(a) =>
      this.actions ++= a
      this.senders :+= sender()
  }


}
