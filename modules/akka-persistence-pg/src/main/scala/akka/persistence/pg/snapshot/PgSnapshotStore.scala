package akka.persistence.pg.snapshot

import akka.persistence.pg.{PgConfig, PgExtension}
import akka.persistence.pg.event.JsonEncoder
import akka.persistence.serialization.Snapshot
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.Serialization

import scala.concurrent.{ExecutionContext, Future}

trait PgSnapshotStore extends SnapshotTable {
  self: PgConfig =>

  def pgExtension: PgExtension
  def serialization: Serialization
  def snapshotEncoder: JsonEncoder = pluginConfig.snapshotEncoder

  import driver.api._

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

  def selectMostRecentSnapshotFor(persistenceId: String, criteria: SnapshotSelectionCriteria)
                                 (implicit executionContext: ExecutionContext): Future[Option[SelectedSnapshot]] = {
    database.run {
      selectSnapshotsQuery(persistenceId, criteria)
        .sortBy(_.sequenceNr.desc)
        .take(1)
        .result.headOption
    } map {
      _ map { entry: SnapshotEntry =>

        val snapshotData: Any = (entry.payload, entry.json, entry.manifest) match {
          case (Some(payload), _, _)              => serialization.deserialize(payload, classOf[Snapshot]).get.data
          case (_, Some(event), Some(manifest))   => snapshotEncoder.fromJson((event, pgExtension.getClassFor[Any](manifest)))
          case _                                  => sys.error( s"""both payload and event are null for snapshot table entry
            with persistenceid='${entry.persistenceId}' and sequencenr='${entry.sequenceNr} and timestamp='${entry.timestamp}'
            This should NEVER happen!""")
        }

        SelectedSnapshot(SnapshotMetadata(entry.persistenceId, entry.sequenceNr, entry.timestamp), snapshotData)
      }
    }
  }

  def selectSnapshotsQuery(persistenceId: String, criteria: SnapshotSelectionCriteria): Query[SnapshotTable, SnapshotEntry, Seq] = {
    snapshots
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNr <= criteria.maxSequenceNr)
      .filter(_.timestamp <= criteria.maxTimestamp)
  }

}
