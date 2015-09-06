package akka.persistence.pg.snapshot

import akka.persistence.pg.util.PgPluginTestUtil

class TestPgSyncSnapshotStore extends PgSyncSnapshotStore {

  override lazy val database = PgPluginTestUtil.initialize(pluginConfig.database, context.system)

}
