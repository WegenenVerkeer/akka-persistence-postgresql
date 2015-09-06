package akka.persistence.pg.journal

import akka.persistence.pg.util.PgPluginTestUtil

class TestPgAsyncWriteJournal extends PgAsyncWriteJournal {

  override lazy val database = PgPluginTestUtil.initialize(pluginConfig.database, context.system)

}