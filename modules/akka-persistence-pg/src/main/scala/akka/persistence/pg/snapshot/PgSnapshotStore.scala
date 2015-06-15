package akka.persistence.pg.snapshot

import akka.persistence.pg.PluginConfig
import akka.persistence.pg.journal.Partitioner
import akka.persistence.serialization.Snapshot
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.Serialization

import scala.concurrent.Future
import scala.language.postfixOps

trait PgSnapshotStore {

  def serialization: Serialization
  def pluginConfig: PluginConfig
  def db = pluginConfig.database
  def partitioner: Partitioner

  import akka.persistence.pg.PgPostgresDriver.api._

  class SnapshotTable(tag: Tag) extends Table[(String, Long, Option[String], Long, Array[Byte])](tag,
    pluginConfig.snapshotSchemaName, pluginConfig.snapshotTableName) {

    def persistenceId       = column[String]("persistenceid")
    def sequenceNr          = column[Long]("sequencenr")
    def partitionKey        = column[Option[String]]("partitionkey")
    def timestamp           = column[Long]("timestamp")
    def snapshot            = column[Array[Byte]]("snapshot")

    def pk = primaryKey("pk_${pluginConfig.snapshotTableName}", (persistenceId, sequenceNr))

    def * = (persistenceId, sequenceNr, partitionKey, timestamp, snapshot)
  }

  val snapshots = TableQuery[SnapshotTable]

  import scala.concurrent.ExecutionContext.Implicits.global

  def writeSnapshot(metadata: SnapshotMetadata, snapshot: Snapshot): Future[Unit]  = {
    db.run {
      snapshots += ((metadata.persistenceId, metadata.sequenceNr, 
        partitioner.partitionKey(metadata.persistenceId),
        metadata.timestamp, serialization.serialize(snapshot).get))
    } map { _  => () }
  }

  def deleteSnapshot(metadata: SnapshotMetadata): Future[Int] = {
    db.run {
      snapshots
        .filter(_.persistenceId === metadata.persistenceId)
        .filter(_.sequenceNr === metadata.sequenceNr)
        .filter(_.partitionKey === partitioner.partitionKey(metadata.persistenceId))
        .delete
    }
  }

  def selectSnapshotsFor(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[List[SelectedSnapshot]] = {
    db.run {
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
      .filter(_.partitionKey === partitioner.partitionKey(persistenceId))
      .filter(_.timestamp <= criteria.maxTimestamp)
  }
}
