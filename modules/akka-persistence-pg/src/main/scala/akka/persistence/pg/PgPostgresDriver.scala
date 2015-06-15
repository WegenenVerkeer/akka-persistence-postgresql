package akka.persistence.pg

import com.github.tminglei.slickpg._

trait PgPostgresDriver extends ExPostgresDriver
  with PgArraySupport
  with PgDate2Support
  with PgJsonSupport
  with PgHStoreSupport {

  //TODO make configurable
  override val pgjson = "json"

  override val api = new API with ArrayImplicits
    with DateTimeImplicits
    with SimpleJsonImplicits
    with HStoreImplicits {}

}

object PgPostgresDriver extends PgPostgresDriver
