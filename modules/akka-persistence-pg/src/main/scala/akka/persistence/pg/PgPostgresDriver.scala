package akka.persistence.pg

import slick.driver.PostgresDriver

trait PgPostgresDriver extends PostgresDriver with AkkaPgJdbcTypes {

  override val api = new API with AkkaPgImplicits {}

}

class PgPostgresDriverImpl(override val pgjson: String) extends PgPostgresDriver

