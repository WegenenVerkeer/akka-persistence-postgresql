package akka.persistence.pg.journal

import akka.actor._
import akka.pattern.pipe
import akka.persistence.pg.PluginConfig
import akka.persistence.pg.journal.StoreActor.{Store, StoreSuccess}

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

private object StoreActor {

  def props(pluginConfig: PluginConfig) = Props(new StoreActor(pluginConfig))

  import slick.dbio.DBIO

  case class Store(actions: Seq[DBIO[_]])
  case object StoreSuccess
}

private class StoreActor(pluginConfig: PluginConfig) extends Actor with ActorLogging {

  case class Done(senders: List[ActorRef], result: Try[Unit])
  case object Run

  import context.dispatcher
  import pluginConfig.pgPostgresProfile.api._

  private var senders: List[ActorRef]                                  = List.empty[ActorRef]
  private var actions: Seq[pluginConfig.pgPostgresProfile.api.DBIO[_]] = Seq.empty[DBIO[_]]

  override def receive: Receive = idle

  def idle: Receive = {
    case Store(as) =>
      this.actions ++= as
      this.senders :+= sender()
      self ! Run
    case Run =>
      if (senders.nonEmpty) {
        val _senders = senders
        pluginConfig.database
          .run(DBIO.seq(this.actions: _*).transactionally)
          .map { _ =>
            Done(_senders, Success(()))
          }
          .recover { case NonFatal(t) => Done(_senders, Failure(t)) }
          .pipeTo(self)
        this.actions = Seq.empty
        this.senders = List.empty
        context become busy
      }
  }

  def busy: Receive = {
    case Done(ss, r) =>
      r match {
        case Success(_) => ss foreach { _ ! StoreSuccess }
        case Failure(t) => ss foreach { _ ! Status.Failure(t) }
      }
      context become idle
      self ! Run
    case Store(a) =>
      this.actions ++= a
      this.senders :+= sender()
  }

}
