package akka.persistence.pg.writestrategy

import com.typesafe.config.ConfigFactory

class TransactionalWriteStrategySuite
    extends MissingWriteStrategySuite(ConfigFactory.load("pg-writestrategy-tx.conf")) {}
