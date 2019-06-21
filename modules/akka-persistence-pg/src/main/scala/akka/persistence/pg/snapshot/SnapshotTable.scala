package akka.persistence.pg.snapshot

import akka.persistence.pg.{JsonString, PgConfig}

case class SnapshotEntry(
    persistenceId: String,
    sequenceNr: Long,
    timestamp: Long,
    manifest: Option[String],
    payload: Option[Array[Byte]],
    json: Option[JsonString]
)

trait SnapshotTable {
  self: PgConfig =>

  import driver.api._

  class SnapshotTable(tag: Tag) extends Table[SnapshotEntry](tag, pluginConfig.schema, pluginConfig.snapshotTableName) {

    def persistenceId  = column[String]("persistenceid")
    def sequenceNr     = column[Long]("sequencenr")
    def timestamp      = column[Long]("timestamp")
    def manifest       = column[Option[String]]("manifest")
    def snapshotBinary = column[Option[Array[Byte]]]("snapshot")
    def snapshotJson   = column[Option[JsonString]]("json")

    def pk = primaryKey(s"pk_${pluginConfig.snapshotTableName}", (persistenceId, sequenceNr))

    def * =
      (persistenceId, sequenceNr, timestamp, manifest, snapshotBinary, snapshotJson) <> (SnapshotEntry.tupled, SnapshotEntry.unapply)
  }

  val snapshots: TableQuery[SnapshotTable] = TableQuery[SnapshotTable]

}
