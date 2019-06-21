package akka.persistence.pg.query

import com.typesafe.config.ConfigFactory

/**
  * uses the TableLocking write strategy and will use the "id" column of the journal
  * table for queries
  */
class EventStoreQueryOnIdTest extends EventStoreQueryTest {

  override lazy val config = ConfigFactory.load("pg-eventstore-locking.conf")

}
