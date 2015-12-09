package akka.persistence.pg.snapshot

import akka.persistence.pg.PgConfig
import akka.persistence.pg.journal.Partitioner
import akka.persistence.serialization.Snapshot
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.Serialization

import scala.concurrent.Future
import scala.language.postfixOps

trait SnapshotTable {
  self: PgConfig =>

  import driver.api._

  class SnapshotTable(tag: Tag) extends Table[(String, Long, Option[String], Long, Array[Byte])](tag,
    pluginConfig.schema, pluginConfig.snapshotTableName) {

    def persistenceId       = column[String]("persistenceid")
    def sequenceNr          = column[Long]("sequencenr")
    def partitionKey        = column[Option[String]]("partitionkey")
    def timestamp           = column[Long]("timestamp")
    def snapshot            = column[Array[Byte]]("snapshot")

    def pk = primaryKey(s"pk_${pluginConfig.snapshotTableName}", (persistenceId, sequenceNr))

    def * = (persistenceId, sequenceNr, partitionKey, timestamp, snapshot)
  }

  val snapshots = TableQuery[SnapshotTable]

}
