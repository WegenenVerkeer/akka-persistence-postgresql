package akka.persistence.pg

import akka.actor.Actor

trait PgActorConfig { self: Actor =>

  def pluginConfig: PluginConfig
  lazy val database = pluginConfig.database
}
