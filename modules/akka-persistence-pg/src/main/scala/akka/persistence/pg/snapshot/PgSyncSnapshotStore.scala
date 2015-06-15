package akka.persistence.pg.snapshot

import akka.actor.{ActorLogging, ActorSystem}
import akka.persistence.pg.{PgExtension, PgPostgresDriver}
import akka.persistence.serialization.Snapshot
import akka.persistence.{SelectedSnapshot, SnapshotMetadata, SnapshotSelectionCriteria}
import akka.serialization.{Serialization, SerializationExtension}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

//TODO uses Await.result for sync calls, make timeout configurable
class PgSyncSnapshotStore extends akka.persistence.snapshot.SnapshotStore
  with PgSnapshotStore with ActorLogging {

  implicit val system: ActorSystem = context.system
  implicit val executionContext = context.system.dispatcher

  override val serialization: Serialization = SerializationExtension(context.system)
  override val pluginConfig = PgExtension(context.system).pluginConfig

  import PgPostgresDriver.api._

  override def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    log.debug(s"loading snapshot for persistenceId: $persistenceId, criteria: $criteria")
    selectSnapshotsFor(persistenceId, criteria) map { _.headOption }
  }

  override def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    log.debug(s"saving snapshot for metadata: $metadata")
    writeSnapshot(metadata, Snapshot(snapshot))
  }

  override def saved(metadata: SnapshotMetadata): Unit = log.debug(s"Saved: $metadata")

  override def delete(metadata: SnapshotMetadata): Unit = {
    log.debug(s"deleting: $metadata")
    Await.result(deleteSnapshot(metadata), 10 seconds)
    ()
  }

  override def delete(persistenceId: String, criteria: SnapshotSelectionCriteria): Unit = {
    log.debug(s"deleting for persistenceId: $persistenceId and criteria: $criteria")
    val deleted: Int = Await.result(db.run { selectSnapshotsQuery(persistenceId, criteria).delete }, 10 seconds)
    log.debug(s"deleted $deleted snapshots")
  }

}
