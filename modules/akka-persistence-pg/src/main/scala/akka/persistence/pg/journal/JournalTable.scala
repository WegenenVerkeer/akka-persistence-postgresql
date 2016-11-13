package akka.persistence.pg.journal

import java.time.OffsetDateTime

import akka.persistence.pg.{JsonString, PgConfig}

case class JournalEntry(id: Option[Long],
                        rowid: Option[Long],
                        persistenceId: String,
                        sequenceNr: Long,
                        deleted: Boolean,
                        payload: Option[Array[Byte]],
                        manifest: String,
                        uuid: String,
                        writerUuid: String,
                        created: OffsetDateTime,
                        tags: Map[String, String],
                        json: Option[JsonString])



/**
 * The journal/event store: it stores persistent messages.
 * Either payload or event must be NOT NULL
 */
trait JournalTable {
  self: PgConfig =>

  import driver.api._

  case class JournalEntryWithReadModelUpdates(entry: JournalEntry,
                                              readModelUpdates: Seq[DBIO[_]])

  class JournalTable(tag: Tag) extends Table[JournalEntry](
    tag, pluginConfig.schema, pluginConfig.journalTableName) {

    def id                  = column[Long]("id", O.AutoInc)
    def rowid               = column[Option[Long]]("rowid")
    def persistenceId       = column[String]("persistenceid")
    def sequenceNr          = column[Long]("sequencenr")
    def deleted             = column[Boolean]("deleted", O.Default(false))
    def payload             = column[Option[Array[Byte]]]("payload")
    def manifest            = column[String]("manifest")
    def uuid                = column[String]("uuid")
    def writerUuid          = column[String]("writeruuid")
    def created             = column[OffsetDateTime]("created", O.Default(OffsetDateTime.now()))
    def tags                = column[Map[String, String]]("tags", O.Default(Map.empty))
    def event               = column[Option[JsonString]]("event")

    def idForQuery =
      if (pluginConfig.idForQuery == "rowid") rowid
      else id.?

    def pk = primaryKey(s"${pluginConfig.journalTableName}_pk", (persistenceId, sequenceNr))

    def * = (id.?, rowid, persistenceId, sequenceNr, deleted, payload, manifest, uuid, writerUuid, created, tags, event) <>
      (JournalEntry.tupled, JournalEntry.unapply)

  }

  val journals = TableQuery[JournalTable]

}
