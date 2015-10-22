package akka.persistence.pg

import com.typesafe.config.ConfigFactory

import scala.language.postfixOps

/**
  * uses the TableLocking write strategy and will use the "id" column of the journal
  * table for queries
  */
class EventStoreQueryOnIdTest extends EventStoreQueryTest {

  override lazy val config = ConfigFactory.load("pg-eventstore-locking.conf")

}