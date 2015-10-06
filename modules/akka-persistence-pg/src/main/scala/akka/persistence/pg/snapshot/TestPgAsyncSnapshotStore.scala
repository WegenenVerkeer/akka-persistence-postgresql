package akka.persistence.pg.snapshot

import akka.persistence.pg.util.PluginTestConfig

class TestPgAsyncSnapshotStore extends PgAsyncSnapshotStore {

  override val pluginConfig = new PluginTestConfig(context.system)

}
