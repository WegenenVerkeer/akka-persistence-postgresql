package akka.persistence.pg

import slick.jdbc.JdbcBackend

trait PgConfig {

  def pluginConfig: PluginConfig
  lazy val driver: PgPostgresProfile         = pluginConfig.pgPostgresProfile
  lazy val database: JdbcBackend.DatabaseDef = pluginConfig.database

}
