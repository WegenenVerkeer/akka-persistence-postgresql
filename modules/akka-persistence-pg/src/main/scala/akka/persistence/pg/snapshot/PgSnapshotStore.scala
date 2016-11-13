package akka.persistence.pg.snapshot

import akka.persistence.pg.PgConfig
import akka.persistence.serialization.Snapshot
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.Serialization

import scala.concurrent.Future
import scala.language.postfixOps

trait PgSnapshotStore extends SnapshotTable {
  self: PgConfig =>

  def serialization: Serialization

  import driver.api._

  import scala.concurrent.ExecutionContext.Implicits.global

  def snapshotsQuery(metadata: SnapshotMetadata) = {
    snapshots
      .filter(_.persistenceId === metadata.persistenceId)
      .filter(_.sequenceNr === metadata.sequenceNr)
  }

  def deleteSnapshot(metadata: SnapshotMetadata): Future[Int] = {
    database.run {
      snapshotsQuery(metadata).delete
    }
  }

  def selectMostRecentSnapshotFor(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    database.run {
      selectSnapshotsQuery(persistenceId, criteria)
        .sortBy(_.sequenceNr.desc)
        .take(1)
        .result.headOption
    } map {
      _ map { r =>
        SelectedSnapshot(SnapshotMetadata(r._1, r._2, r._3), serialization.deserialize(r._4, classOf[Snapshot]).get.data)
      }
    }
  }

  def selectSnapshotsQuery(persistenceId: String, criteria: SnapshotSelectionCriteria): Query[SnapshotTable, (String, Long, Long, Array[Byte]), Seq] = {
    snapshots
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNr <= criteria.maxSequenceNr)
      .filter(_.timestamp <= criteria.maxTimestamp)
  }

}
