package akka.persistence.pg

import akka.actor._
import akka.persistence.Persistence
import akka.persistence.pg.event.PgEventReader

import scala.reflect.ClassTag

object PgExtension extends ExtensionId[PgExtension] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): PgExtension = new PgExtension(system)

  override def lookup() = PgExtension

}

class PgExtension(system: ExtendedActorSystem) extends Extension {

  val persistence = Persistence(system)

  private val DefaultPluginDispatcherId = "akka.persistence.dispatchers.default-plugin-dispatcher"
  val pluginConfig = PluginConfig(system)

  system.registerOnTermination {
    pluginConfig.shutdownDataSource()
  }

  def getClassFor[T: ClassTag](s: String): Class[_ <: T] = {
    system.dynamicAccess.getClassFor[T](s).getOrElse(sys.error(s"could not find class with name $s"))
  }

  def actorRefOf(s: String): ActorRef = {
    system.provider.resolveActorRef(s)
  }

  lazy val eventReader: ActorRef = {
    //TODO allow to configure separate dispatcher
    //val pluginDispatcherId = if (pluginConfig.hasPath("plugin-dispatcher")) pluginConfig.getString("plugin-dispatcher") else dispatcherSelector(pluginClass)
    system.systemActorOf(Props[PgEventReader].withDispatcher(DefaultPluginDispatcherId), "pg-eventreader")
  }



}
