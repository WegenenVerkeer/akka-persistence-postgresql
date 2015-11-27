package akka.persistence.pg.journal

import java.time.OffsetDateTime
import java.util.UUID

import akka.persistence.PersistentRepr
import akka.persistence.pg.event.{Created, EventTagger, JsonEncoder}
import akka.persistence.pg.{PgConfig, PgExtension}
import akka.serialization.Serialization
import play.api.libs.json.JsValue

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

case class JournalEntry(id: Option[Long],
                        rowid: Option[Long],
                        persistenceId: String,
                        sequenceNr: Long,
                        partitionKey: Option[String],
                        deleted: Boolean,
                        payload: Option[Array[Byte]],
                        payloadManifest: String,
                        manifest: String,
                        uuid: String,
                        writerUuid: String,
                        created: OffsetDateTime,
                        tags: Map[String, String],
                        json: Option[JsValue])

case class JournalEntryWithEvent(entry: JournalEntry, event: Any)


/**
 * The journal/event store: it stores persistent messages.
 * Either payload or event must be NOT NULL
 */
trait JournalStore {
  self: PgConfig =>

  def serialization: Serialization

  def pgExtension: PgExtension

  def eventEncoder: JsonEncoder = pluginConfig.eventStoreConfig.eventEncoder

  def eventTagger: EventTagger = pluginConfig.eventStoreConfig.eventTagger

  def partitioner: Partitioner = pluginConfig.journalPartitioner

  import driver.MappedJdbcType
  import driver.api._

  class JournalTable(tag: Tag) extends Table[JournalEntry](
    tag, pluginConfig.journalSchemaName, pluginConfig.journalTableName) {

    def id = column[Long]("id", O.AutoInc)

    def rowid = column[Option[Long]]("rowid")

    def persistenceId = column[String]("persistenceid")

    def sequenceNr = column[Long]("sequencenr")

    def partitionKey = column[Option[String]]("partitionkey")

    def deleted = column[Boolean]("deleted", O.Default(false))

    def payload = column[Option[Array[Byte]]]("payload")

    def payloadManifest = column[String]("payloadmf")

    def manifest = column[String]("manifest")

    def uuid = column[String]("uuid")

    def writerUuid = column[String]("writeruuid")

    def created = column[OffsetDateTime]("created", O.Default(OffsetDateTime.now()))

    def tags = column[Map[String, String]]("tags", O.Default(Map.empty))

    def event = column[Option[JsValue]]("event")

    def idForQuery = {
      if (pluginConfig.eventStoreConfig.idColumnName == "rowid") {
        rowid
      } else {
        id.?
      }
    }

    def pk = primaryKey(s"${pluginConfig.journalTableName}_pk", (persistenceId, sequenceNr))

    def * = (id
      .?, rowid, persistenceId, sequenceNr, partitionKey, deleted, payload, payloadManifest, manifest, uuid, writerUuid, created, tags, event) <>
      (JournalEntry.tupled, JournalEntry.unapply)

  }

  val journals = TableQuery[JournalTable]
  lazy val rowIdSequence = Sequence[Long](pluginConfig.fullRowIdSequenceName)

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

  def deleteMessageRange(persistenceId: String, toSequenceNr: Long): Future[Int] = {
    database.run(
      journals
        .filter(_.persistenceId === persistenceId)
        .filter(_.sequenceNr <= toSequenceNr)
        .delete
    )
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

  def toJournalEntries(messages: Seq[PersistentRepr]): Try[Seq[JournalEntryWithEvent]] = {
    Try {
      messages map { message =>

        val (tags, event) = eventTagger.tag(message.persistenceId, message.payload)
        val (payloadAsJson, payloadAsBytes) = serializePayload(event)

        JournalEntryWithEvent(
          JournalEntry(
            None,
            None,
            message.persistenceId,
            message.sequenceNr,
            partitioner.partitionKey(message.persistenceId),
            deleted = false,
            payloadAsBytes,
            event.getClass.getName,
            message.manifest,
            getUuid(event),
            message.writerUuid,
            getCreated(event),
            tags,
            payloadAsJson),
          event
        )
      }
    }
  }

  def toPersistentRepr(entry: JournalEntry): PersistentRepr = {
    def toRepr(a: Any) =
      PersistentRepr(
        payload = a,
        sequenceNr = entry.sequenceNr,
        persistenceId = entry.persistenceId,
        manifest = entry.manifest,
        deleted = entry.deleted,
        sender = null, // sender ActorRef
        writerUuid = entry.writerUuid
      )

    val clazz = pgExtension.getClassFor[Any](entry.payloadManifest)



    (entry.payload, entry.json) match {
      case (Some(payload), _) => toRepr(serialization.deserialize(payload, clazz).get)
      case (_, Some(event)) => toRepr(eventEncoder.fromJson((event.value, clazz)))
      case (None, None) => sys.error( s"""both payload and event are null for journal table entry
            with id=${entry.id}, (persistenceid='${entry.persistenceId}' and sequencenr='${entry.sequenceNr}')
            This should NEVER happen!""")
    }
  }

}
