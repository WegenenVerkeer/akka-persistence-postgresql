package akka.persistence.pg

import slick.jdbc.PostgresProfile

trait PgPostgresProfile extends PostgresProfile with AkkaPgJdbcTypes {

  override val api = new API with AkkaPgImplicits {}

}

class PgPostgresProfileImpl(override val pgjson: String) extends PgPostgresProfile
