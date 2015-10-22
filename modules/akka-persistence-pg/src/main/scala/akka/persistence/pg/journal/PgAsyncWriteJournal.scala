package akka.persistence.pg.journal

import akka.actor.ActorLogging
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.pg.{PgConfig, PgExtension}
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.serialization.{Serialization, SerializationExtension}

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class PgAsyncWriteJournal extends AsyncWriteJournal
  with ActorLogging
  with PgConfig
  with JournalStore {

  implicit val executionContext = context.system.dispatcher

  override val serialization: Serialization = SerializationExtension(context.system)
  override val pgExtension: PgExtension = PgExtension(context.system)
  override lazy val pluginConfig = pgExtension.pluginConfig

  lazy val writeStrategy = pluginConfig.writeStrategy(this.context)

  import driver.api._

  def storeActions(entries: Seq[JournalEntryWithReadModelUpdates]): Seq[DBIO[_]] = {
    val storeActions: Seq[DBIO[_]] = Seq(journals ++= entries.map(_.entry))
    storeActions ++ entries.flatMap(_.readModelUpdates)
  }

  override def asyncWriteMessages(writes: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    log.debug(s"asyncWriteMessages {} atomicWrites", writes.size)
    val batches = writes map { atomicWrite => toJournalEntries(atomicWrite.payload) }
    val storedBatches = batches map {
      case Failure(t)     => Future.successful(Failure(t))
      case Success(batch) => writeStrategy.store(storeActions(batch)).map(Success(_))
    }
    Future sequence storedBatches
  }

  override def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    log.debug(s"Async read for highest sequence number for processorId: [$persistenceId] (hint, seek from  nr: [$fromSequenceNr])")
    database.run {
      journals
        .filter(_.persistenceId === persistenceId)
        .filter(byPartitionKey(persistenceId))
        .map((table: JournalTable) => table.sequenceNr)
        .max
        .result
    } map {
      _.getOrElse(0)
    }
  }

  private[this] def byPartitionKey(persistenceId: String): (JournalTable) => Rep[Option[Boolean]] = {
    j =>
      val partitionKey = partitioner.partitionKey(persistenceId)
      j.partitionKey.isEmpty && partitionKey.isEmpty || j.partitionKey === partitionKey
  }

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)
                                  (replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    log.debug(s"Async replay for processorId [$persistenceId], from sequenceNr: [$fromSequenceNr], to sequenceNr: [$toSequenceNr] with max records: [$max]")
    database.run {
      journals
        .filter(_.persistenceId === persistenceId)
        .filter(_.sequenceNr >= fromSequenceNr)
        .filter(_.sequenceNr <= toSequenceNr)
        .filter(byPartitionKey(persistenceId))
        .sortBy(_.sequenceNr)
        .take(max)
        .result
    } map {
      _.map(toPersistentRepr).foreach(replayCallback)
    }
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    //TODO we could alternatively permanently delete all but the last message and mark the last message as deleted
    val selectedEntries: Query[JournalTable, JournalEntry, Seq] = journals
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNr <= toSequenceNr)
      .filter(byPartitionKey(persistenceId))
      .sortBy(_.sequenceNr.desc)

    database.run(selectedEntries.map(_.deleted).update(true)).map(_ => ())
  }

}