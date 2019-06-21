package akka.persistence.pg.journal

import akka.persistence.pg.util.PluginTestConfig

class TestPgAsyncWriteJournal extends PgAsyncWriteJournal {

  override lazy val pluginConfig = new PluginTestConfig(context.system)

}
