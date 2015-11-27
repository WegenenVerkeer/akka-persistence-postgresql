package akka.persistence.pg

import com.github.tminglei.slickpg._

trait PgPostgresDriver extends ExPostgresDriver
  with PgArraySupport
  with PgDate2Support
  with PgPlayJsonSupport
  with PgHStoreSupport {

  override val api = new API with ArrayImplicits
    with DateTimeImplicits
    with PlayJsonImplicits
    with HStoreImplicits {}

}

class PgPostgresDriverImpl(override val pgjson: String) extends PgPostgresDriver

