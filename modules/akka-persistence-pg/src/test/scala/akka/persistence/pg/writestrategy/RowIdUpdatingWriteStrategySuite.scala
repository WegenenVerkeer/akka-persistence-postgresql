package akka.persistence.pg.writestrategy

import com.typesafe.config.ConfigFactory

class RowIdUpdatingWriteStrategySuite
    extends NonMissingWriteStrategySuite(ConfigFactory.load("pg-writestrategy-rowid.conf"))
