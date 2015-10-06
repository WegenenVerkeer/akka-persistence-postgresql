package akka.persistence.pg.journal

import java.sql.BatchUpdateException

import akka.actor.ActorLogging
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.pg.{PgConfig, PgExtension}
import akka.persistence.pg.event.StoredEvent
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.serialization.{Serialization, SerializationExtension}

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

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

  def storeActions(entries: Seq[JournalEntryWithEvent]): Seq[DBIO[_]] = {

    val storeActions: Seq[DBIO[_]] = Seq(journals ++= entries.map(_.entry))

    val actions: Seq[DBIO[_]] = pluginConfig.eventStore match {
      case None        => storeActions
      case Some(store) => storeActions ++ store.postStoreActions(entries
        .filter { _.entry.json.isDefined }
        .map { entryWithEvent: JournalEntryWithEvent => StoredEvent(entryWithEvent.entry.persistenceId, entryWithEvent.event) }
      )
    }
    actions
  }

  def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    log.debug(s"asyncWriteMessages {} messages", messages.size)
    val entries: immutable.Seq[Try[Seq[JournalEntryWithEvent]]] = messages map { atomicWrite => toJournalEntries(atomicWrite.payload) }
    val entries2Store = entries.filter(_.isSuccess).flatMap(_.get)
    val r = writeStrategy.store(storeActions(entries2Store))
    r.onFailure {
      case t: BatchUpdateException => log.error(t.getNextException, "problem storing events")
      case NonFatal(t) => log.error(t, "problem storing events")
    }
    r map { _ =>
      if (entries.count(_.isFailure) == 0) Nil
      else {
        entries.map { (entry: Try[Seq[JournalEntryWithEvent]]) =>
          entry match {
            case Success(_) => Success(())
            case Failure(t) => Failure(t)
          }
        }
      }
    }
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
    val selectedEntries = journals
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNr <= toSequenceNr)
      .filter(byPartitionKey(persistenceId))

    //TODO check if messages should be permanently deleted or not
//    val action = if (permanent) {
//        selectedEntries.delete
//    } else {
//        selectedEntries.map(_.deleted).update(true)
//    }
    database.run(selectedEntries.map(_.deleted).update(true)).map(_ => ())
  }

}