package akka.persistence.pg.writestrategy

import com.typesafe.config.ConfigFactory

class SingleThreadedWriteStrategySuite
    extends NonMissingWriteStrategySuite(ConfigFactory.load("pg-writestrategy-st.conf"))
