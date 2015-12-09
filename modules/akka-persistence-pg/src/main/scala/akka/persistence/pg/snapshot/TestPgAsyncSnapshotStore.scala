package akka.persistence.pg.snapshot

import akka.persistence.pg.util.PluginTestConfig

class TestPgAsyncSnapshotStore extends PgAsyncSnapshotStore {

  override lazy val pluginConfig = new PluginTestConfig(context.system)

}
