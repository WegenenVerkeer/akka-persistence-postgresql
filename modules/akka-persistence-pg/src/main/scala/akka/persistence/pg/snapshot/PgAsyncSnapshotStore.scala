package akka.persistence.pg.snapshot

import akka.actor.{ActorLogging, ActorSystem}
import akka.persistence.pg.journal.Partitioner
import akka.persistence.pg.{PgConfig, PgExtension}
import akka.persistence.serialization.Snapshot
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.{Serialization, SerializationExtension}
import slick.dbio.Effect.Write
import slick.profile.FixedSqlAction

import scala.concurrent.{Await, Future}

class PgAsyncSnapshotStore extends akka.persistence.snapshot.SnapshotStore
  with PgSnapshotStore
  with ActorLogging
  with PgConfig {

  implicit val system: ActorSystem = context.system
  implicit val executionContext = context.system.dispatcher

  override val serialization: Serialization = SerializationExtension(context.system)
  override lazy val pluginConfig = PgExtension(context.system).pluginConfig
  override def partitioner: Partitioner = pluginConfig.journalPartitioner

  import driver.api._

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    log.debug(s"loading snapshot for persistenceId: {}, criteria: {}", persistenceId, criteria)
    selectMostRecentSnapshotFor(persistenceId, criteria)
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    log.debug(s"saving snapshot for metadata {}",metadata)
    val serialized: Array[Byte] = serialization.serialize(Snapshot(snapshot)).get
    database.run(snapshotsQuery(metadata).length
      .result.flatMap { result: Int =>
        val v = (metadata.persistenceId, metadata.sequenceNr, partitioner.partitionKey(metadata.persistenceId), metadata.timestamp, serialized)
        if (result > 0) {
          snapshotsQuery(metadata).update(v)
        } else {
          snapshots += v
        }
    }).map { _ => () }
  }

  override def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    log.debug(s"deleting: {}",metadata)
    deleteSnapshot(metadata).map { _ =>
      log.debug(s"deleted snapshot {}",metadata)
    }
  }

  override def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    log.debug(s"deleting for persistenceId: {} and criteria: {}",persistenceId, criteria)
    database.run(selectSnapshotsQuery(persistenceId, criteria).delete).map { deleted =>
      log.debug(s"deleted {} snapshots", deleted); ()
    }
  }

}
