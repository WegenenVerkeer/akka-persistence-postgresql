package akka.persistence.pg

import akka.actor._
import akka.pattern.ask
import akka.persistence.Persistence
import akka.persistence.pg.journal.RowIdUpdater.IsBusy

import scala.reflect.ClassTag
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps

object PgExtension extends ExtensionId[PgExtension] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): PgExtension = new PgExtension(system)

  override def lookup() = PgExtension

}

class PgExtension(system: ExtendedActorSystem) extends Extension {

  val persistence = Persistence(system)

  val pluginConfig = PluginConfig(system)

  system.registerOnTermination {
    pluginConfig.shutdownDataSource()
  }

  def whenDone[T](t: => Future[T]): Future[T] = {

    import system.dispatcher

    implicit val timeout = Timeout(5 seconds)

    def isBusy: Future[Boolean] =
      system
        .actorSelection("/user/AkkaPgRowIdUpdater")
        .resolveOne()
        .flatMap { rowIdUpdater =>
          rowIdUpdater ? IsBusy
        }
        .mapTo[Boolean]
        .recover {
          case e: ActorNotFound => false
        }

    def go(): Future[T] = isBusy.flatMap {
      case true  => Thread.sleep(100); go()
      case false => t
    }

    go()

  }

  def terminateWhenReady(): Future[Terminated] = whenDone(system.terminate())

  def getClassFor[T: ClassTag](s: String): Class[_ <: T] =
    system.dynamicAccess.getClassFor[T](s).getOrElse(sys.error(s"could not find class with name $s"))

}
