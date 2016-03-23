package akka.persistence.pg.event

import java.time.OffsetDateTime

import akka.persistence.pg.{PgConfig, EventStoreConfig}
import play.api.libs.json.JsValue

case class Event(id: Long,
                 persistenceId: String,
                 sequenceNr: Long,
                 uuid: String,
                 created: OffsetDateTime,
                 tags: Map[String, String],
                 className: String,
                 event: JsValue)

case class StoredEvent(persistenceId: String, event: Any)

trait EventStore {
  self: PgConfig =>

  def eventStoreConfig: EventStoreConfig = pluginConfig.eventStoreConfig

  import driver.api._

  //This is basically just a another mapping on the same journal table, ideally you would create a DB view
  class EventsTable(tag: Tag) extends Table[Event](
    tag, pluginConfig.eventStoreConfig.schemaName, pluginConfig.eventStoreConfig.tableName) {

    def id                  = column[Long](pluginConfig.idForQuery)
    def persistenceId       = column[String]("persistenceid")
    def sequenceNr          = column[Long]("sequencenr")
    def uuid                = column[String]("uuid")
    def created             = column[OffsetDateTime]("created")
    def tags                = column[Map[String, String]]("tags")
    def className           = column[String]("manifest")
    def event               = column[JsValue]("event")

    def * = (id, persistenceId, sequenceNr, uuid, created, tags, className, event) <> (Event.tupled, Event.unapply)

  }

  val events = TableQuery[EventsTable]


  /**
   * find all events for a specific persistenceId
   * @param persistenceId the persistenceId
   */
  def findEvents(persistenceId: String): Query[EventsTable, Event, Seq] = {
    events
      .filter(_.persistenceId === persistenceId)
      .filter(_.event.?.isDefined)
  }

  /**
   * find all events starting from a specific id with specific tags
   * @param fromId the id to start from
   * @param tags the tags that must be present
   * @param max maximum number of events to return
   * @return the list of corresponding events
   */
  def findEvents(fromId: Long, tags: Map[String, String] = Map.empty, max: Long = Long.MaxValue): Query[EventsTable, Event, Seq] = {
    events
      .filter(_.id >= fromId)
      .filter(_.event.?.isDefined)
      .filter(_.tags @> tags.bind)
      .sortBy(_.id)
      .take(max)
  }


}

