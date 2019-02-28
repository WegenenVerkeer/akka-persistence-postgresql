package akka.persistence.pg.journal

import java.sql.BatchUpdateException

import akka.actor._
import akka.pattern._
import akka.persistence.JournalProtocol.{RecoverySuccess, ReplayMessagesFailure}
import akka.persistence.journal.AsyncWriteJournal
import akka.persistence.pg.journal.PgAsyncWriteJournal._
import akka.persistence.pg.{EventTag, PgConfig, PgExtension, PluginConfig}
import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.serialization.{Serialization, SerializationExtension}
import akka.stream.actor.ActorPublisherMessage.Cancel
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.stream.scaladsl.{Keep, Sink, Source}
import slick.jdbc.{ResultSetConcurrency, ResultSetType}

import scala.collection.{immutable, mutable}
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class PgAsyncWriteJournal
  extends AsyncWriteJournal
  with ActorLogging
  with PgConfig
  with JournalStore {

  implicit val executionContext: ExecutionContextExecutor = context.system.dispatcher

  override val serialization: Serialization = SerializationExtension(context.system)
  override val pgExtension: PgExtension = PgExtension(context.system)
  override lazy val pluginConfig: PluginConfig = pgExtension.pluginConfig

  lazy val writeStrategy: WriteStrategy = pluginConfig.writeStrategy(this.context)

  import driver.api._

  def storeActions(entries: Seq[JournalEntryInfo]): Seq[DBIO[_]] = {
    val storeEventsActions: Seq[DBIO[_]] = Seq(journals ++= entries.map(_.entry))
    val extraDBIOActions: Seq[DBIO[_]] = entries.flatMap(_.extraDBIOInfo).map(_.action)
    storeEventsActions ++ extraDBIOActions
  }

  def failureHandlers(entries: Seq[JournalEntryInfo]): Seq[PartialFunction[Throwable, Unit]] = {
    entries.flatMap(_.extraDBIOInfo).map(_.failureHandler)
  }

  override def asyncWriteMessages(writes: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    log.debug("asyncWriteMessages {} atomicWrites", writes.size)
    val batches: immutable.Seq[Try[Seq[JournalEntryInfo]]] = writes map { atomicWrite => toJournalEntries(atomicWrite.payload) }

    def storeBatch(entries: Seq[JournalEntryInfo]): Future[Try[Unit]] = {
      val result = writeStrategy
        .store(storeActions(entries), new Notifier(entries.map(_.entry), this))
        .map { Success.apply }
      result.failed.foreach {
        case e: BatchUpdateException => log.error(e.getNextException, "problem storing events")
        case NonFatal(e)             => log.error(e, "problem storing events")
        case _                       =>
      }
      result
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
    log.debug("Async read for highest sequence number for processorId: [{}] (hint, seek from  nr: [{}])", persistenceId, fromSequenceNr)
    database.run {
      journals
        .filter(_.persistenceId === persistenceId)
        .map((table: JournalTable) => table.sequenceNr)
        .max
        .result
    } map {
      _.getOrElse(0)
    }
  }

  implicit val materializer = ActorMaterializer(Some(ActorMaterializerSettings(context.system).withInputBuffer(16, 1024)))

  override def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)
                                  (replayCallback: (PersistentRepr) => Unit): Future[Unit] = {

    log.debug("Async replay for persistenceId [{}], from sequenceNr: [{}], to sequenceNr: [{}] with max records: [{}]",
      persistenceId, fromSequenceNr, toSequenceNr, max)

    val publisher = database.stream {
      journals
        .filter(_.persistenceId === persistenceId)
        .filter(_.sequenceNr >= fromSequenceNr)
        .filter(_.sequenceNr <= toSequenceNr)
        .sortBy(_.sequenceNr)
        .take(max)
        .result
        .withStatementParameters(rsType = ResultSetType.ForwardOnly, rsConcurrency = ResultSetConcurrency.ReadOnly, fetchSize = 1000)
        .transactionally
    }

    Source.fromPublisher(publisher)
      .toMat(
        Sink.foreach[JournalTable#TableElementType] { e =>
          replayCallback(toPersistentRepr(e))
        }
      )(Keep.right).run().map(_ => ())
  }

  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    //TODO we could alternatively permanently delete all but the last message and mark the last message as deleted
    val selectedEntries: Query[JournalTable, JournalEntry, Seq] = journals
      .filter(_.persistenceId === persistenceId)
      .filter(_.sequenceNr <= toSequenceNr)
      .filter(_.deleted === false)
      .sortBy(_.sequenceNr.desc)

    database.run(selectedEntries.map(_.deleted).update(true)).map(_ => ())
  }


  // ------------------------------------------------------------
  // --- Akka Persistence Query logic ------

  override def receivePluginInternal: Receive = {

    case Cancel => cancelSubscribers()

    // requested to send events containing given tags between from and to rowId
    case ReplayTaggedMessages(fromRowId, toRowId, max, tags, replyTo) =>
      handleReplayTaggedMessages(fromRowId, toRowId, max, tags, replyTo)

    // requested to send events containing given tags between from and to rowId
    case ReplayMessages(fromRowId, toRowId, max, replyTo) =>
      handleReplayMessages(fromRowId, toRowId, max, replyTo)

    // subscribe sender to tag notification
    case SubscribeTags(tags) => addTagSubscriber(sender, tags)

    //subscribe sender for all events
    case SubscribeAllEvents => addSubscriber(sender)

    case SubscribePersistenceId(persistenceId) => addPersistenceIdSubscriber(sender, persistenceId)

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
      } else {
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

  private def handleReplayMessages(fromRowId: Long, toRowId: Long, max: Long, replyTo: ActorRef): Unit = {

    val correctedFromRowId = math.max(0L, fromRowId - 1)

    asyncReadHighestRowId(correctedFromRowId).flatMap { highestRowId =>

      val calculatedToRowId = math.min(toRowId, highestRowId)

      if (highestRowId == 0L || fromRowId > calculatedToRowId) {
        // we are done if there is nothing to send
        Future.successful(highestRowId)
      } else {
        asyncReplayMessagesBoundedByRowIds(fromRowId, calculatedToRowId, max) {
          case ReplayedEventMessage(persistentRepr, offset) =>
            adaptFromJournal(persistentRepr).foreach { adaptedPersistentRepr =>
              replyTo.tell(ReplayedEventMessage(adaptedPersistentRepr, offset), Actor.noSender)
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

  def asyncReadHighestRowId(fromRowId: Long): Future[Long] = {
    val query =
      journals
        .filter(_.idForQuery >= fromRowId)
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
      log.debug("Replaying {} events  ({} <= rowId <= {} and tags: {})", entries.size, fromRowId, toRowId, tags)
      entries.foreach { entry =>
        val persistentRepr = toPersistentRepr(entry)
        replayCallback(ReplayedTaggedMessage(persistentRepr, tags, idForQuery(entry)))
      }
    }
  }

  def asyncReplayMessagesBoundedByRowIds(fromRowId: Long, toRowId: Long, max: Long)
                                        (replayCallback: ReplayedEventMessage => Unit): Future[Unit] = {

    val query =
      journals
        .filter(_.idForQuery >= fromRowId)
        .filter(_.idForQuery <= toRowId)
        .sortBy(_.idForQuery)
        .take(max)

    database
      .run(query.result)
      .map { entries =>
        log.debug("Replaying {} events  ({} <= rowId <= {})", entries.size, fromRowId, toRowId)
        entries.foreach { entry =>
          val persistentRepr = toPersistentRepr(entry)
          replayCallback(ReplayedEventMessage(persistentRepr, idForQuery(entry)))
        }
      }
  }

  private def idForQuery(entry: JournalEntry): Long = {
    val id = if (pluginConfig.idForQuery == "rowid") entry.rowid else entry.id
    id.getOrElse(sys.error("something went wrong, probably a misconfiguration"))
  }

  def notifyEventsAvailable(entries: Seq[JournalEntry]): Unit = {
    var persistenceIds = Set.empty[String]
    entries foreach { entry =>
      //notify event with tag available
      if (hasTagSubscribers) entry.tags.foreach(notifyTagChange)
      persistenceIds += entry.persistenceId
    }

    //notify event for persistenceId available
    if (hasPersistenceIdSubscribers) {
      persistenceIds.foreach(notifyPersistenceIdChange)
    }

    //notify event available
    notifyEventsAdded()
  }

  def cancelSubscribers() = {
    persistenceIdSubscribers.foreach { _._2.foreach(_ ! Cancel) }
    tagSubscribers.foreach { _._2.foreach(_ ! Cancel) }
    allEventsSubscribers.foreach { _ ! Cancel }
  }

  private val persistenceIdSubscribers = new mutable.HashMap[String, mutable.Set[ActorRef]] with mutable.MultiMap[String, ActorRef]
  private val tagSubscribers = new mutable.HashMap[EventTag, mutable.Set[ActorRef]] with mutable.MultiMap[EventTag, ActorRef]
  private var allEventsSubscribers = Set.empty[ActorRef]

  protected[journal] def hasPersistenceIdSubscribers: Boolean = persistenceIdSubscribers.nonEmpty
  protected[journal] def hasTagSubscribers: Boolean = tagSubscribers.nonEmpty

  private def addTagSubscriber(subscriber: ActorRef, eventTags: Set[EventTag]): Unit = {
    eventTags.foreach(eventTag => tagSubscribers.addBinding(eventTag, subscriber))
    log.debug(s"added subscriptions for {} for actor {}", eventTags, subscriber)
    // watch allEventsSubscribers in order to unsubscribe them if they terminate
    context.watch(subscriber)
    ()
  }

  private def addSubscriber(subscriber: ActorRef): Unit = {
    allEventsSubscribers += subscriber
    log.debug("added subscriptions for actor {}", subscriber)
    context.watch(subscriber)
    ()
  }

  private def addPersistenceIdSubscriber(subscriber: ActorRef, persistenceId: String): Unit = {
    persistenceIdSubscribers.addBinding(persistenceId, subscriber)
    context.watch(subscriber)
    ()
  }

  private def removeSubscriber(subscriber: ActorRef): Unit = {
    log.warning("actor {} terminated!!", subscriber)

    val keys = persistenceIdSubscribers.collect { case (k, s) if s.contains(subscriber) => k }
    keys.foreach { key => persistenceIdSubscribers.removeBinding(key, subscriber) }

    val tags = tagSubscribers.collect { case (k, s) if s.contains(subscriber) => k }
    if (tags.nonEmpty) {
      log.debug("removing subscriber {} [tags: {}]", subscriber, tags)
      tags.foreach { tag => tagSubscribers.removeBinding(tag, subscriber) }
    }

    allEventsSubscribers -= subscriber
  }

  private def notifyEventsAdded(): Unit = {
    allEventsSubscribers.foreach(_ ! NewEventAppended)
  }

  private def notifyPersistenceIdChange(persistenceId: String): Unit =
    if (persistenceIdSubscribers.contains(persistenceId)) {
      persistenceIdSubscribers(persistenceId).foreach(_ ! NewEventAppended)
    }

  private def notifyTagChange(eventTag: EventTag): Unit =
    if (tagSubscribers.contains(eventTag)) {
      log.debug("Notify subscriber of new events with tag: {}", eventTag)
      tagSubscribers(eventTag).foreach(_ ! NewEventAppended)
    }

}


object PgAsyncWriteJournal {

  sealed trait SubscriptionCommand

  //message send when a new event is appended relevant for the subscriber
  object NewEventAppended extends DeadLetterSuppression

  //events replay by tags
  final case class SubscribeTags(tags: Set[EventTag]) extends SubscriptionCommand
  final case class ReplayTaggedMessages(fromRowId: Long, toRowId: Long, max: Long,
                                        tags: Set[EventTag], replyTo: ActorRef) extends SubscriptionCommand
  final case class ReplayedTaggedMessage(persistent: PersistentRepr, tags: Set[EventTag], offset: Long)
    extends DeadLetterSuppression with NoSerializationVerificationNeeded

  //all events replay
  object SubscribeAllEvents extends SubscriptionCommand
  final case class ReplayMessages(fromRowId: Long, toRowId: Long, max: Long, replyTo: ActorRef)
    extends SubscriptionCommand
  final case class ReplayedEventMessage(persistent: PersistentRepr, offset: Long)
    extends DeadLetterSuppression with NoSerializationVerificationNeeded

  //events by persistenceId
  final case class SubscribePersistenceId(persistenceId: String) extends SubscriptionCommand

}