package akka.persistence.pg.journal

import akka.pattern.pipe
import akka.actor._
import akka.persistence.pg.journal.StoreActor.{StoreSuccess, Store}
import akka.persistence.pg.{PgConfig, PluginConfig}

import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

private object StoreActor {

  def props(pluginConfig: PluginConfig) = Props(new StoreActor(pluginConfig))

  import slick.dbio.DBIO

  case class Store(actions: Seq[DBIO[_]])
  case object StoreSuccess
}

private class StoreActor(override val pluginConfig: PluginConfig) extends Actor
  with ActorLogging
  with PgConfig {

  case class Done(senders: List[ActorRef], result: Try[Unit])
  case object Run

  import driver.api._
  import context.dispatcher

  var senders: List[ActorRef] = List.empty[ActorRef]
  var actions: Seq[driver.api.DBIO[_]] = Seq.empty[DBIO[_]]

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
