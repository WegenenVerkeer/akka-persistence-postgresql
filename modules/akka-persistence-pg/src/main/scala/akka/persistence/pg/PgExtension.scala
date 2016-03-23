package akka.persistence.pg

import akka.actor._
import akka.persistence.Persistence

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

}
