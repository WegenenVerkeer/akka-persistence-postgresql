package akka.persistence.pg.snapshot

import akka.persistence.pg.util.PgPluginTestUtil

class TestPgAsyncSnapshotStore extends PgAsyncSnapshotStore {

  override lazy val database = PgPluginTestUtil.initialize(pluginConfig.database, context.system)

}
