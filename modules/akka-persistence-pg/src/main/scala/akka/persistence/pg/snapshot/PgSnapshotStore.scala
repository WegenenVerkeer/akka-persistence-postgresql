package akka.persistence.pg.snapshot

import akka.event.LoggingAdapter
import akka.persistence.pg.{PgConfig, PgExtension}
import akka.persistence.pg.event.JsonEncoder
import akka.persistence.serialization.Snapshot
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.Serialization

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait PgSnapshotStore extends SnapshotTable {
  self: PgConfig =>

  def pgExtension: PgExtension
  def serialization: Serialization
  def snapshotEncoder: JsonEncoder = pluginConfig.snapshotEncoder
  def log: LoggingAdapter

  import driver.api._

  def snapshotsQuery(metadata: SnapshotMetadata): Query[SnapshotTable, SnapshotEntry, Seq] =
    snapshots
      .filter(_.persistenceId === metadata.persistenceId)
      .filter(_.sequenceNr === metadata.sequenceNr)

  def deleteSnapshot(metadata: SnapshotMetadata): Future[Int] =
    database.run {
      snapshotsQuery(metadata).delete
    }

  def selectMostRecentSnapshotFor(persistenceId: String, criteria: SnapshotSelectionCriteria)(
      implicit executionContext: ExecutionContext
  ): Future[Option[SelectedSnapshot]] =
    database.run {
      selectSnapshotsQuery(persistenceId, criteria)
        .sortBy(_.sequenceNr.desc)
        .take(1)
        .result
        .headOption
    } map {
      _ flatMap { entry: SnapshotEntry =>
        ((entry.payload, entry.json, entry.manifest) match {
          case (Some(payload), _, _) =>
            deserialize(persistenceId, serialization.deserialize(payload, classOf[Snapshot]).get.data)
          case (_, Some(event), Some(manifest)) =>
            deserialize(persistenceId, snapshotEncoder.fromJson((event, pgExtension.getClassFor[Any](manifest))))
          case _ =>
            sys.error(s"""both payload and event are null for snapshot table entry
            with persistenceid='${entry.persistenceId}' and sequencenr='${entry.sequenceNr} and timestamp='${entry.timestamp}'
            This should NEVER happen!""")
        }) map {
          SelectedSnapshot(SnapshotMetadata(entry.persistenceId, entry.sequenceNr, entry.timestamp), _)
        }

      }
    }

  def deserialize(persistenceId: String, snapshot: => Any): Option[Any] =
    Try(snapshot) match {
      case Success(data) => Some(data)
      case Failure(t) =>
        if (pluginConfig.ignoreSnapshotDecodingFailure) {
          log.warning(
            "problem deserializing snapshot with persistenceId '{}' from store using Akka serialization: {}",
            persistenceId,
            t.getMessage
          )
          None
        } else {
          throw t
        }

    }

  def selectSnapshotsQuery(
      persistenceId: String,
      criteria: SnapshotSelectionCriteria
  ): Query[SnapshotTable, SnapshotEntry, Seq] =
    snapshots
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNr <= criteria.maxSequenceNr)
      .filter(_.timestamp <= criteria.maxTimestamp)

}
