package akka.persistence.pg

trait PgConfig {

  def pluginConfig: PluginConfig
  lazy val driver = pluginConfig.pgPostgresDriver
  lazy val database = pluginConfig.database
}
