package akka.persistence.pg.snapshot

import akka.persistence.pg.util.PluginTestConfig

class TestPgSyncSnapshotStore extends PgSyncSnapshotStore {

  override lazy val pluginConfig = new PluginTestConfig(context.system)

}
