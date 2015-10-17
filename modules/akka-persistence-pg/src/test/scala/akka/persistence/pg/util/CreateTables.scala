package akka.persistence.pg.util

import akka.persistence.pg.PgConfig

/**
  * Created by peter on 18/10/15.
  */
trait CreateTables {
  self: PgConfig =>

  import driver.api._

  lazy val createJournal = sqlu"""create table #${pluginConfig.fullJournalTableName} (
                           "id" BIGSERIAL NOT NULL PRIMARY KEY,
                           "rowid" BIGINT DEFAULT NULL,
                           "persistenceid" VARCHAR(254) NOT NULL,
                           "sequencenr" INT NOT NULL,
                           "partitionkey" VARCHAR(254) DEFAULT NULL,
                           "deleted" BOOLEAN DEFAULT false,
                           "sender" VARCHAR(512),
                           "payload" BYTEA,
                           "payloadmf" VARCHAR(512),
                           "uuid" VARCHAR(254) NOT NULL,
                           "created" timestamptz NOT NULL,
                           "tags" HSTORE,
                           "event" JSON,
                           constraint "cc_journal_payload_event" check (payload IS NOT NULL OR event IS NOT NULL))"""

  lazy val createSnapshot = sqlu"""create table #${pluginConfig.fullSnapshotTableName} (
                            "persistenceid" VARCHAR(254) NOT NULL,
                            "sequencenr" INT NOT NULL,
                            "partitionkey" VARCHAR(254) DEFAULT NULL,
                            "timestamp" bigint NOT NULL,
                            "snapshot" BYTEA,
                            PRIMARY KEY (persistenceid, sequencenr))"""

  lazy val createRowIdSequence = sqlu"""create sequence #${pluginConfig.fullRowIdSequenceName}"""

  lazy val createTables = createJournal.andThen(createSnapshot).andThen(createRowIdSequence)

  def countEvents                = sql"""select count(*) from #${pluginConfig.fullJournalTableName}""".as[Long].head
  def countEvents(id: String)    = sql"""select count(*) from #${pluginConfig.fullJournalTableName} where persistenceid = $id""".as[Long].head
  def countSnapshots(id: String) = sql"""select count(*) from #${pluginConfig.fullSnapshotTableName} where persistenceid = $id""".as[Long].head


}
