package akka.persistence.pg.journal

import java.sql.BatchUpdateException

import akka.actor._
import akka.persistence.JournalProtocol.{ReplayMessagesFailure, RecoverySuccess}
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.pg.event.{StoredEvent, EventStore}
import akka.persistence.pg.{PgConfig, PgExtension}
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.serialization.{Serialization, SerializationExtension}

import scala.collection.{immutable, mutable}
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.util.Try
import akka.persistence.pg.journal.PgAsyncWriteJournal._
import akka.pattern._
import akka.persistence.pg.EventTag

class PgAsyncWriteJournal
  extends AsyncWriteJournal
  with ActorLogging
  with PgConfig
  with JournalStore {

  implicit val executionContext = context.system.dispatcher

  override val serialization: Serialization = SerializationExtension(context.system)
  override val pgExtension: PgExtension = PgExtension(context.system)
  override lazy val pluginConfig = pgExtension.pluginConfig

  lazy val writeStrategy = pluginConfig.writeStrategy(this.context)

  import driver.api._

  def storeActions(entries: Seq[JournalEntryInfo]): Seq[DBIO[_]] = {
    val storeEventsActions: Seq[DBIO[_]] = Seq(journals ++= entries.map(_.entry))
    val readModelUpdateActions: Seq[DBIO[_]] = entries.flatMap(_.readModelInfo).map(_.action) ++
      pluginConfig.eventStore.fold (Seq.empty[DBIO[_]]) { (store: EventStore) =>
        store.postStoreActions(entries.map { info => StoredEvent(info.entry.persistenceId, info.payload) })
    }
    storeEventsActions ++ readModelUpdateActions
  }

  def failureHandlers(entries: Seq[JournalEntryInfo]): Seq[PartialFunction[Throwable, Unit]] = {
    entries.flatMap(_.readModelInfo).map(_.failureHandler)
  }

  override def asyncWriteMessages(writes: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    log.debug(s"asyncWriteMessages {} atomicWrites", writes.size)
    val batches: immutable.Seq[Try[Seq[JournalEntryInfo]]] = writes map { atomicWrite => toJournalEntries(atomicWrite.payload) }

    def storeBatch(entries: Seq[JournalEntryInfo]): Future[Try[Unit]] = {
      val r: Future[Unit] = writeStrategy.store(storeActions(entries))
      r.onFailure {
        case e: BatchUpdateException => log.error(e.getNextException, "problem storing events")
      }
      r.map {
        entries foreach { info =>
          info.entry.tags.foreach(notifyTagChange)
        }
        Success.apply
      }
    }

    val storedBatches = batches map {
      case Failure(t)     => Future.successful(Failure(t))
      case Success(batch) =>
        failureHandlers(batch).toList match {
          case Nil =>       storeBatch(batch)
          case h :: Nil =>  storeBatch(batch).recover { case e: Throwable if h.isDefinedAt(e) => Failure(e)  }
          case _ =>         Future.failed(new RuntimeException("you can only have one failureHandler for each AtomicWrite"))
        }
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

    log.debug(s"Async replay for processorId [$persistenceId], " +
      s"from sequenceNr: [$fromSequenceNr], to sequenceNr: [$toSequenceNr] with max records: [$max]")

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


  // ------------------------------------------------------------
  // --- Akka Persistence Query logic ------

  override def receivePluginInternal: Receive = {

    // requested to send events containing given tags between from and to rowId
    case ReplayTaggedMessages(fromRowId, toRowId, max, tags, replyTo) =>
      handleReplayTaggedMessages(fromRowId, toRowId, max, tags, replyTo)

    // subscribe sender to tag nofification
    case SubscribeTags(tags) => addTagSubscriber(tags)

    // unsubscribe terminated actor
    case Terminated(ref) => removeSubscriber(ref)
  }


  private def handleReplayTaggedMessages(fromRowId: Long, toRowId: Long, max: Long,
                                         eventTags: Set[EventTag], replyTo: ActorRef): Unit = {


    val correctedFromRowId = math.max(0L, fromRowId - 1)

    asyncReadHighestRowIdWithTags(eventTags, correctedFromRowId).flatMap { highestRowId =>

      val calculatedToRowId = math.min(toRowId, highestRowId)

      if (highestRowId == 0L || fromRowId > calculatedToRowId) {
        // we are done if there is nothing to send
        Future.successful(highestRowId)
      }
      else {
        asyncReplayTaggedMessagesBoundedByRowIds(eventTags, fromRowId, calculatedToRowId, max) {
          case ReplayedTaggedMessage(persistentRepr, tags, offset) =>
            adaptFromJournal(persistentRepr).foreach { adaptedPersistentRepr =>
              replyTo.tell(ReplayedTaggedMessage(adaptedPersistentRepr, tags, offset), Actor.noSender)
            }
        }.map(_ => highestRowId)
      }
    } map {
      highestRowId => RecoverySuccess(highestRowId)
    } recover {
      case e => ReplayMessagesFailure(e)
    } pipeTo replyTo

    ()
  }


  /**
   * build a 'or' filter for tags
   * will select Events containing at least one of the EventTags
   */
  private def tagsFilter(tags: Set[EventTag]) = {
    (table: JournalTable) => {
      tags
        .map { case (tagKey, tagValue) => table.tags @> Map(tagKey -> tagValue.value).bind }
        .reduceLeftOption(_ || _)
        .getOrElse(false: Rep[Boolean])
    }
  }

  def asyncReadHighestRowIdWithTags(tags: Set[EventTag], fromRowId: Long): Future[Long] = {


    val query =
      journals
        .filter(_.idForQuery >= fromRowId)
        .filter(tagsFilter(tags))
        .map(_.idForQuery)
        .max

    database
      .run(query.result)
      .map(_.getOrElse(0L)) // we don't want an Option[Long], but a Long

  }

  def asyncReplayTaggedMessagesBoundedByRowIds(tags: Set[EventTag], fromRowId: Long, toRowId: Long, max: Long)
                                              (replayCallback: ReplayedTaggedMessage => Unit): Future[Unit] = {

    val query =
      journals
        .filter(_.idForQuery >= fromRowId)
        .filter(_.idForQuery <= toRowId)
        .filter(tagsFilter(tags))
        .sortBy(_.idForQuery)
        .take(max)

    database
      .run(query.result)
      .map { entries =>
      log.debug(s"Replaying ${entries.size} events  ($fromRowId <= rowId <= $toRowId and $tags)")
      entries.foreach { entry =>
        val persistentRepr = toPersistentRepr(entry)
        replayCallback(ReplayedTaggedMessage(persistentRepr, tags, idForQuery(entry)))
      }
    }
  }

  private def idForQuery(entry: JournalEntry): Long = {
    val id = if (pluginConfig.idForQuery == "rowid") {
      entry.rowid
    } else {
      entry.id
    }
    id.getOrElse(sys.error("something went wrong, probably a misconfiguration"))
  }


  private val tagSubscribers = new mutable.HashMap[EventTag, mutable.Set[ActorRef]] with mutable.MultiMap[EventTag, ActorRef]

  private def addTagSubscriber(eventTags: Set[EventTag]): Unit = {
    val subscriber = sender()
    eventTags.foreach(eventTag => tagSubscribers.addBinding(eventTag, subscriber))
    log.debug(s"added subscriptions for $eventTags for actor $subscriber")
    // watch subscribers in order to unsubscribe them if they terminate
    context.watch(subscriber)
    ()
  }

  protected def removeSubscriber(subscriber: ActorRef): Unit = {
    log.warning(s"Actor $subscriber terminated!!")
    val tags = tagSubscribers.collect { case (k, s) if s.contains(subscriber) => k }
    if (tags.nonEmpty) {
      log.debug(s"removing subscriber $subscriber [tags: $tags]")
      tags.foreach { tag => tagSubscribers.removeBinding(tag, subscriber) }
    }
  }

  protected def notifyTagChange(eventTag: EventTag): Unit =
    if (tagSubscribers.contains(eventTag)) {
      log.debug(s"Notify subscriber of new events with tag: $eventTag")
      val changed = PgAsyncWriteJournal.TaggedEventAppended(eventTag)
      tagSubscribers(eventTag).foreach(_ ! changed)
    }

}


object PgAsyncWriteJournal {

  sealed trait SubscriptionCommand

  final case class SubscribeTags(tags: Set[EventTag]) extends SubscriptionCommand

  final case class TaggedEventAppended(eventTag: EventTag) extends DeadLetterSuppression

  final case class ReplayTaggedMessages(fromRowId: Long, toRowId: Long, max: Long,
                                        tags: Set[EventTag], replyTo: ActorRef) extends SubscriptionCommand

  final case class ReplayedTaggedMessage(persistent: PersistentRepr, tags: Set[EventTag], offset: Long)
    extends DeadLetterSuppression with NoSerializationVerificationNeeded

}