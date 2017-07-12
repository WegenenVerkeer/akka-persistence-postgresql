package akka.persistence.pg.snapshot

import akka.persistence.pg.PgConfig

trait SnapshotTable {
  self: PgConfig =>

  import driver.api._

  class SnapshotTable(tag: Tag) extends Table[(String, Long, Long, Array[Byte])](tag,
    pluginConfig.schema, pluginConfig.snapshotTableName) {

    def persistenceId       = column[String]("persistenceid")
    def sequenceNr          = column[Long]("sequencenr")
    def timestamp           = column[Long]("timestamp")
    def snapshot            = column[Array[Byte]]("snapshot")

    def pk = primaryKey(s"pk_${pluginConfig.snapshotTableName}", (persistenceId, sequenceNr))

    def * = (persistenceId, sequenceNr, timestamp, snapshot)
  }

  val snapshots = TableQuery[SnapshotTable]

}
