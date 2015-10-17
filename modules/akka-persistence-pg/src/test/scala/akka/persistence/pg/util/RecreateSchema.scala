package akka.persistence.pg.util

import akka.persistence.pg.PgConfig

trait RecreateSchema {
  self: PgConfig =>

  def schemaName: String

  import driver.api._

  lazy val dropSchema = sqlu"""drop schema if exists "#$schemaName" cascade"""
  lazy val createSchema = sqlu"""create schema "#$schemaName""""

  lazy val recreateSchema = dropSchema.andThen(createSchema)

}
