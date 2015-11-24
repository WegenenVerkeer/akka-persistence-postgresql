package akka.persistence.pg.journal

import java.time.OffsetDateTime
import java.util.UUID

import akka.actor.ActorRef
import akka.persistence.PersistentRepr
import akka.persistence.pg.event._
import akka.persistence.pg.{PgConfig, PgExtension}
import akka.serialization.Serialization
import play.api.libs.json.JsValue

import scala.concurrent.Future
import scala.util.Try

/**
 * The journal/event store: it stores persistent messages.
 * Either payload or event must be NOT NULL
 */
trait JournalStore extends JournalTable {
  self: PgConfig =>

  def serialization: Serialization
  def pgExtension: PgExtension
  def eventEncoder: JsonEncoder = pluginConfig.eventStoreConfig.eventEncoder
  def eventTagger: EventTagger = pluginConfig.eventStoreConfig.eventTagger
  def partitioner: Partitioner = pluginConfig.journalPartitioner

  import driver.api._

  type JournalEntryWithReadModelUpdate = (JournalEntry, DBIO[_])

  import scala.concurrent.ExecutionContext.Implicits.global

  def selectMessage(persistenceId: String, sequenceNr: Long): Future[Option[PersistentRepr]] = {
    database.run(
      journals
        .filter(_.persistenceId === persistenceId)
        .filter(_.sequenceNr === sequenceNr)
        .result
    ) map {
      _.headOption.map(toPersistentRepr)
    }
  }

  private[this] def serializePayload(payload: Any): (Option[JsValue], Option[Array[Byte]]) = {
    if (eventEncoder.toJson.isDefinedAt(payload)) {
      val json = eventEncoder.toJson(payload)
      require(eventEncoder.fromJson.isDefinedAt((json, payload.getClass)),
        s"You MUST always be able to decode what you encoded, fromJson method is incomplete for ${payload.getClass}")
      (Some(json), None)
    } else {
      val o: AnyRef = payload.asInstanceOf[AnyRef]
      (None, Some(serialization.findSerializerFor(o).toBinary(o)))
    }
  }

  def getCreated(event: Any): OffsetDateTime = event match {
    case e: Created => e.created
    case _ => OffsetDateTime.now()
  }

  def getUuid(event: Any): String = {
    UUID.randomUUID.toString
  }

  def toJournalEntries(messages: Seq[PersistentRepr]): Try[Seq[JournalEntryWithReadModelUpdate]] = {
    Try {
      messages map { message =>
        val event = message.payload match {
          case w: EventWrapper[_] => w.event
          case _ => message.payload
        }
        val tags: Map[String, String] = eventTagger.tag(message.payload)
        val update: DBIO[_] = message.payload match {
          case r: ReadModelUpdate => r.readModelAction
          case _ => DBIO.successful(())
        }

        val (payloadAsJson, payloadAsBytes) = serializePayload(event)
        (JournalEntry(None,
          None,
          message.persistenceId,
          message.sequenceNr,
          partitioner.partitionKey(message.persistenceId),
          deleted = false,
          payloadAsBytes,
          event.getClass.getName,
          getUuid(event),
          message.writerUuid,
          getCreated(event),
          tags,
          payloadAsJson), update)
      }
    }
  }

  def toPersistentRepr(entry: JournalEntry): PersistentRepr = {
    def toRepr(a: Any) =
      PersistentRepr(
        payload = a,
        sequenceNr = entry.sequenceNr,
        persistenceId = entry.persistenceId,
        manifest = "",
        deleted = entry.deleted,
        sender = ActorRef.noSender,
        writerUuid = entry.writerUuid
      )

    val clazz = pgExtension.getClassFor[Any](entry.manifest)

    (entry.payload, entry.json) match {
      case (Some(payload), _) => toRepr(serialization.deserialize(payload, clazz).get)
      case (_, Some(event)) => toRepr(eventEncoder.fromJson((event.value, clazz)))
      case (None, None) => sys.error( s"""both payload and event are null for journal table entry
            with id=${entry.id}, (persistenceid='${entry.persistenceId}' and sequencenr='${entry.sequenceNr}')
            This should NEVER happen!""")
    }
  }

}
