package akka.persistence.pg.journal

import java.time.OffsetDateTime
import java.util.UUID

import akka.actor.ActorRef
import akka.persistence.PersistentRepr
import akka.persistence.pg.event.{Created, EventTagger, JsonEncoder}
import akka.persistence.pg.{PgExtension, PluginConfig}
import akka.serialization.Serialization
import play.api.libs.json.JsValue

import scala.concurrent.Future

case class JournalEntry(id: Option[Long],
                        persistenceId: String,
                        sequenceNr: Long,
                        partitionKey: Option[String],
                        deleted: Boolean,
                        sender: ActorRef,
                        payload: Option[Array[Byte]],
                        payloadManifest: String,
                        uuid: String,
                        created: OffsetDateTime,
                        tags: Map[String, String],
                        json: Option[JsValue])

case class JournalEntryWithEvent(entry: JournalEntry, event: Any)


/**
 * The journal/event store: it stores persistent messages.
 * Either payload or event must be NOT NULL
 */
trait JournalStore {

  def serialization: Serialization
  def pgExtension: PgExtension
  def pluginConfig: PluginConfig
  def eventEncoder: JsonEncoder
  def eventTagger: EventTagger
  def partitioner: Partitioner
  def db = pluginConfig.database

  import akka.persistence.pg.PgPostgresDriver.MappedJdbcType
  import akka.persistence.pg.PgPostgresDriver.api._

  implicit val actorRefMapper = MappedJdbcType.base[ActorRef, String](Serialization.serializedActorPath,
    pgExtension.actorRefOf(_))

  class JournalTable(tag: Tag) extends Table[JournalEntry](
    tag, pluginConfig.journalSchemaName, pluginConfig.journalTableName) {

    def id                  = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def persistenceId       = column[String]("persistenceid")
    def sequenceNr          = column[Long]("sequencenr")
    def partitionKey        = column[Option[String]]("partitionkey")
    def deleted             = column[Boolean]("deleted", O.Default(false))
    def sender              = column[ActorRef]("sender")
    def payload             = column[Option[Array[Byte]]]("payload")
    def payloadManifest     = column[String]("payloadmf")
    def uuid                = column[String]("uuid")
    def created             = column[OffsetDateTime]("created", O.Default(OffsetDateTime.now()))
    def tags                = column[Map[String, String]]("tags", O.Default(Map.empty))
    def event               = column[Option[JsValue]]("event")

    def pIdSeqNrIndex = index(s"idx_${pluginConfig.journalTableName}_pid_seq", (persistenceId, sequenceNr), unique = true)

    def * = (id.?, persistenceId, sequenceNr, partitionKey, deleted, sender, payload, payloadManifest, uuid, created, tags, event) <>
      (JournalEntry.tupled, JournalEntry.unapply _)

  }

  val journals = TableQuery[JournalTable]

  import scala.concurrent.ExecutionContext.Implicits.global

  def selectMessage(persistenceId: String, sequenceNr: Long): Future[Option[PersistentRepr]] = {
    db.run(
      journals
        .filter(_.persistenceId === persistenceId)
        .filter(_.sequenceNr === sequenceNr)
        .result
    ) map { _.headOption.map(toPersistentRepr)}
  }

  def deleteMessageRange(persistenceId: String, toSequenceNr: Long): Future[Int] = {
    db.run(
      journals
        .filter(_.persistenceId === persistenceId)
        .filter(_.sequenceNr <= toSequenceNr)
        .delete
    )
  }

  private[this] def serializePayload(payload: Any): (Option[JsValue], Option[Array[Byte]]) = {
    if (eventEncoder.toJson.isDefinedAt(payload)) {
      val json = eventEncoder.toJson(payload)
      require (eventEncoder.fromJson.isDefinedAt((json, payload.getClass)),
        s"You MUST always be able to decode what you encoded, fromJson method is incomplete for ${payload.getClass}")
      (Some(json), None)
    } else {
      val o: AnyRef = payload.asInstanceOf[AnyRef]
      (None, Some(serialization.findSerializerFor(o).toBinary(o)))
    }
  }

  private[this] def getCreated(event: Any): OffsetDateTime = event match {
    case e: Created => e.created
    case _ => OffsetDateTime.now()
  }

  private[this] def getUuid(event: Any): String = {
    UUID.randomUUID.toString
  }

  def toJournalEntries(messages: Seq[PersistentRepr]): Seq[JournalEntryWithEvent] = {
    messages map { message =>
      val (tags, event) = eventTagger.tag(message.persistenceId, message.payload)
      val (payloadAsJson, payloadAsBytes) = serializePayload(event)

      JournalEntryWithEvent(JournalEntry(None,
        message.persistenceId,
        message.sequenceNr,
        partitioner.partitionKey(message.persistenceId),
        deleted = false,
        message.sender,
        payloadAsBytes,
        event.getClass.getName,
        getUuid(message.payload),
        getCreated(message.payload),
        tags,
        payloadAsJson), event)
    }
  }

  def toPersistentRepr(entry : JournalEntry): PersistentRepr = {
    def toRepr(a: Any) = PersistentRepr(a, entry.sequenceNr, entry.persistenceId,
      entry.deleted, sender = entry.sender)
    val clazz = pgExtension.getClassFor[Any](entry.payloadManifest)

    (entry.payload, entry.json) match {
      case (Some(payload), _) => toRepr(serialization.deserialize(payload, clazz).get)
      case (_, Some(event))   => toRepr(eventEncoder.fromJson((event.value, clazz)))
      case (None, None)       => sys.error(s"""both payload and event are null for journal table entry
            with id=${entry.id}, (persistenceid='${entry.persistenceId}' and sequencenr='${entry.sequenceNr}')
            This should NEVER happen!""")
    }
  }

}
