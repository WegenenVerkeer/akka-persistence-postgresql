package akka.persistence.pg.snapshot

import akka.actor.{ActorLogging, ActorSystem}
import akka.persistence.pg.{JsonString, PgConfig, PgExtension, PluginConfig}
import akka.persistence.serialization.Snapshot
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.{Serialization, SerializationExtension}

import scala.concurrent.{ExecutionContextExecutor, Future}

class PgAsyncSnapshotStore
    extends akka.persistence.snapshot.SnapshotStore
    with PgSnapshotStore
    with ActorLogging
    with PgConfig {

  implicit val system: ActorSystem                        = context.system
  implicit val executionContext: ExecutionContextExecutor = context.system.dispatcher

  override val serialization: Serialization    = SerializationExtension(context.system)
  override val pgExtension: PgExtension        = PgExtension(context.system)
  override lazy val pluginConfig: PluginConfig = PgExtension(context.system).pluginConfig

  import driver.api._

  override def loadAsync(
      persistenceId: String,
      criteria: SnapshotSelectionCriteria
  ): Future[Option[SelectedSnapshot]] = {
    log.debug(s"loading snapshot for persistenceId: {}, criteria: {}", persistenceId, criteria)
    selectMostRecentSnapshotFor(persistenceId, criteria)
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    log.debug(s"saving snapshot for metadata {}", metadata)
    val (payloadAsJson, payloadAsBytes) = serializeSnapshot(snapshot)
    val snapshotEntry = SnapshotEntry(
      metadata.persistenceId,
      metadata.sequenceNr,
      metadata.timestamp,
      Some(snapshot.getClass.getName),
      payloadAsBytes,
      payloadAsJson
    )

    //TODO use native upsert
    database
      .run(snapshotsQuery(metadata).length.result.flatMap { result: Int =>
        if (result > 0) {
          snapshotsQuery(metadata).update(snapshotEntry)
        } else {
          snapshots += snapshotEntry
        }
      })
      .map { _ =>
        ()
      }
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    log.debug(s"deleting: {}", metadata)
    deleteSnapshot(metadata).map { _ =>
      log.debug(s"deleted snapshot {}", metadata)
    }
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    log.debug(s"deleting for persistenceId: {} and criteria: {}", persistenceId, criteria)
    database.run(selectSnapshotsQuery(persistenceId, criteria).delete).map { deleted =>
      log.debug(s"deleted {} snapshots", deleted); ()
    }
  }

  private[this] def serializeSnapshot(snapshot: Any): (Option[JsonString], Option[Array[Byte]]) =
    if (snapshotEncoder.toJson.isDefinedAt(snapshot)) {
      val json = snapshotEncoder.toJson(snapshot)
      require(
        snapshotEncoder.fromJson.isDefinedAt((json, snapshot.getClass)),
        s"You MUST always be able to decode what you encoded, fromJson method is incomplete for ${snapshot.getClass}"
      )
      (Some(json), None)
    } else {
      (None, Some(serialization.serialize(Snapshot(snapshot)).get))
    }

}
