package akka.persistence.pg.snapshot

import akka.persistence.pg.PgConfig
import akka.persistence.pg.journal.Partitioner
import akka.persistence.serialization.Snapshot
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.Serialization

import scala.concurrent.Future
import scala.language.postfixOps

trait PgSnapshotStore {
  self: PgConfig =>

  def serialization: Serialization
  def partitioner: Partitioner = pluginConfig.journalPartitioner

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

  import scala.concurrent.ExecutionContext.Implicits.global

  def snapshotsQuery(metadata: SnapshotMetadata) = {
    snapshots
      .filter(_.persistenceId === metadata.persistenceId)
      .filter(_.sequenceNr === metadata.sequenceNr)
      .filter(byPartitionKey(metadata.persistenceId))
  }

  def deleteSnapshot(metadata: SnapshotMetadata): Future[Int] = {
    database.run {
      snapshotsQuery(metadata).delete
    }
  }

  def selectSnapshotsFor(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[List[SelectedSnapshot]] = {
    database.run {
      selectSnapshotsQuery(persistenceId, criteria)
        .sortBy(_.sequenceNr.desc)
        .result
    } map {
      _ map { r =>
        SelectedSnapshot(SnapshotMetadata(r._1, r._2, r._4), serialization.deserialize(r._5, classOf[Snapshot]).get.data)
      } toList
    }
  }

  def selectSnapshotsQuery(persistenceId: String, criteria: SnapshotSelectionCriteria): Query[SnapshotTable, (String, Long, Option[String], Long, Array[Byte]), Seq] = {
    snapshots
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNr <= criteria.maxSequenceNr)
      .filter(byPartitionKey(persistenceId))
      .filter(_.timestamp <= criteria.maxTimestamp)
  }

  private[this] def byPartitionKey(persistenceId: String): (SnapshotTable) => Rep[Option[Boolean]] = {
    s => {
      val partitionKey = partitioner.partitionKey(persistenceId)
      s.partitionKey.isEmpty && partitionKey.isEmpty || s.partitionKey === partitionKey
    }
  }
}
