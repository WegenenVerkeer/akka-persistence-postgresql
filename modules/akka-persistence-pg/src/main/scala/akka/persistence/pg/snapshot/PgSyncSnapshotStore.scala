package akka.persistence.pg.snapshot

import akka.actor.{ActorLogging, ActorSystem}
import akka.persistence.pg.journal.Partitioner
import akka.persistence.pg.{PgConfig, PgExtension}
import akka.persistence.serialization.Snapshot
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.{Serialization, SerializationExtension}

import scala.concurrent.{Await, Future}

class PgSyncSnapshotStore extends akka.persistence.snapshot.SnapshotStore
  with PgSnapshotStore
  with ActorLogging
  with PgConfig {

  implicit val system: ActorSystem = context.system
  implicit val executionContext = context.system.dispatcher

  override val serialization: Serialization = SerializationExtension(context.system)
  override val pluginConfig = PgExtension(context.system).pluginConfig
  override def partitioner: Partitioner = pluginConfig.journalPartitioner

  import driver.api._

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    log.debug(s"loading snapshot for persistenceId: $persistenceId, criteria: $criteria")
    selectSnapshotsFor(persistenceId, criteria) map { _.headOption }
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    log.debug(s"saving snapshot for metadata: $metadata")
    database.run(
      snapshots += ((metadata.persistenceId, metadata.sequenceNr,
        partitioner.partitionKey(metadata.persistenceId),
        metadata.timestamp, serialization.serialize(Snapshot(snapshot)).get))
    ) map { _  =>
      log.debug(s"snapshot saved $metadata")
      ()
    }
  }

  override def saved(metadata: SnapshotMetadata): Unit = {
    log.debug(s"Saved: $metadata")
  }

  override def delete(metadata: SnapshotMetadata): Unit = {
    log.debug(s"deleting: $metadata")
    Await.result(deleteSnapshot(metadata), pluginConfig.snapshotDeleteAwaitDuration)
    ()
  }

  override def delete(persistenceId: String, criteria: SnapshotSelectionCriteria): Unit = {
    log.debug(s"deleting for persistenceId: $persistenceId and criteria: $criteria")
    val deleted: Int = Await.result(database.run { selectSnapshotsQuery(persistenceId, criteria).delete }, pluginConfig.snapshotDeleteAwaitDuration)
    log.debug(s"deleted $deleted snapshots")
  }

}
