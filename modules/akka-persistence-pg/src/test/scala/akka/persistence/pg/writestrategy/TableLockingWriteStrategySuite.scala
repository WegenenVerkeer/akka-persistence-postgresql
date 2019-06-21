package akka.persistence.pg.writestrategy

import com.typesafe.config.ConfigFactory

class TableLockingWriteStrategySuite
    extends NonMissingWriteStrategySuite(ConfigFactory.load("pg-writestrategy-locking.conf")) {}
