package akka.persistence.pg.snapshot

import akka.persistence.pg.util.PgPluginTestUtil
import slick.jdbc.JdbcBackend

class TestPgSyncSnapshotStore extends PgSyncSnapshotStore {

  override val db = PgPluginTestUtil.initialize(pluginConfig.database, context.system)

}
