package akka.persistence.pg.query

import com.typesafe.config.ConfigFactory

/**
  * uses the default TableLocking write strategy and will use the "id" column of the journal
  * table for queries
  */
class EventStoreQueryOnIdNotificationTest extends EventStoreQueryNotificationTest {

  override lazy val config = ConfigFactory.load("pg-eventstore-locking.conf")

}
