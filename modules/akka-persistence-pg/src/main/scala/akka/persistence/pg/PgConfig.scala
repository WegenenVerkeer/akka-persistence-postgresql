package akka.persistence.pg

trait PgConfig {

  def pluginConfig: PluginConfig
  lazy val driver = pluginConfig.pgPostgresProfile
  lazy val database = pluginConfig.database
}
