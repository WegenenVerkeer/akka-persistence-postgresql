package akka.persistence.pg

import akka.persistence.pg.event.EventStore

class TestEventStore(override val pluginConfig: PluginConfig) extends EventStore with PgConfig
