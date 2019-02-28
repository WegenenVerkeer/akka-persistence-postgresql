package akka.persistence.pg.journal

import java.time.OffsetDateTime
import java.util.UUID

import akka.persistence.PersistentRepr
import akka.persistence.pg.event._
import akka.persistence.pg.{EventTag, JsonString, PgConfig, PgExtension}
import akka.serialization.Serialization

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

  import driver.api._

  case class ExtraDBIOInfo(action: DBIO[_], failureHandler: PartialFunction[Throwable, Unit])
  case class JournalEntryInfo(entry: JournalEntry, payload: Any, extraDBIOInfo: Option[ExtraDBIOInfo])

  private[this] def serializePayload(payload: Any): (Option[JsonString], Option[Array[Byte]]) = {
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

  /**
    * Returns the timestamp an event was created.
    * By default this will return the created timestamp if your event extens Created, otherwise it returns the current time.
    * @param event any event
    * @return the timestamp this event was created
    */
  def getCreated(event: Any): OffsetDateTime = event match {
    case e: Created => e.created
    case _ => OffsetDateTime.now()
  }

  /**
    * Returns a unique id for this event. By default this just generates a new UUID.
    * @param event any event
    * @return the unique id of the event
    */
  def getUuid(event: Any): String = {
    UUID.randomUUID.toString
  }

  def toJournalEntries(messages: Seq[PersistentRepr]): Try[Seq[JournalEntryInfo]] = {
    Try {
      messages map { message =>
        val event = message.payload match {
          case w: EventWrapper[_] => w.event
          case _ => message.payload
        }
        val tags: Map[String, String] = eventTagger.tags(message.payload)
        val update: Option[ExtraDBIOInfo] = message.payload match {
          case r: ExtraDBIOSupport => Some(ExtraDBIOInfo(r.extraDBIO, r.failureHandler))
          case _ => None
        }

        val (payloadAsJson, payloadAsBytes) = serializePayload(event)
        JournalEntryInfo(JournalEntry(None,
          None,
          message.persistenceId,
          message.sequenceNr,
          deleted = false,
          payloadAsBytes,
          event.getClass.getName,
          getUuid(event),
          message.writerUuid,
          getCreated(event),
          tags,
          payloadAsJson), event, update)
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
        sender = null, //sender ActorRef
        writerUuid = entry.writerUuid
      )

    val clazz = pgExtension.getClassFor[Any](entry.manifest)

    (entry.payload, entry.json) match {
      case (Some(payload), _) => toRepr(serialization.deserialize(payload, clazz).get)
      case (_, Some(event)) => toRepr(eventEncoder.fromJson((event, clazz)))
      case (None, None) => sys.error( s"""both payload and event are null for journal table entry
            with id=${entry.id}, (persistenceid='${entry.persistenceId}' and sequencenr='${entry.sequenceNr}')
            This should NEVER happen!""")
    }
  }

  /**
    * build a 'or' filter for tags
    * will select Events containing at least one of the EventTags
    */
  protected def tagsFilter(tags: Set[EventTag]): JournalTable => Rep[Boolean] = {
    table: JournalTable => {
      tags
        .map { case (tagKey, tagValue) => table.tags @> Map(tagKey -> tagValue.value).bind }
        .reduceLeftOption(_ || _)
        .getOrElse(false: Rep[Boolean])
    }
  }

}
