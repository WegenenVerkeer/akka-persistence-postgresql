package akka.persistence.pg.snapshot

import akka.persistence.pg.util.PgPluginTestUtil
import slick.jdbc.JdbcBackend

class TestPgSyncSnapshotStore extends PgSyncSnapshotStore {

  println("initializing TestPgSyncSnapshotStore")
  override val db = PgPluginTestUtil.initialize(pluginConfig.database, context.system)

}
