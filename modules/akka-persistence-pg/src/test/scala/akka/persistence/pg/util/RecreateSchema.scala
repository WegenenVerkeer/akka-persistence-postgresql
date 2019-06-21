package akka.persistence.pg.util

import akka.persistence.pg.PgConfig

trait RecreateSchema {
  self: PgConfig =>

  import driver.api._

  lazy val dropSchema   = sqlu"""drop schema if exists #${pluginConfig.schemaName} cascade"""
  lazy val createSchema = sqlu"""create schema #${pluginConfig.schemaName}"""

  lazy val recreateSchema = dropSchema.andThen(createSchema)

}
