package akka.persistence.pg.util

import akka.actor.ActorSystem
import akka.persistence.pg.{PgExtension, PluginConfig}
import slick.jdbc.JdbcBackend

class PluginTestConfig(system: ActorSystem) extends PluginConfig(system.settings.config) {

  override lazy val database: JdbcBackend.DatabaseDef = {
    PgPluginTestUtil.initialize(createDatabase, system)
  }
}
