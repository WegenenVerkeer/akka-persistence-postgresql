package akka.persistence.pg.journal

import akka.persistence.pg.util.PgPluginTestUtil

class TestPgAsyncWriteJournal extends PgAsyncWriteJournal {

  println("initializing TestPgAsyncWriteJournal")
  override val db = PgPluginTestUtil.initialize(pluginConfig.database, context.system)

}