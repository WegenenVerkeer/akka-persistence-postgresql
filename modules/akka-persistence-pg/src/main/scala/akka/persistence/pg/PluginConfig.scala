package akka.persistence.pg

import java.util.Properties

import akka.actor.{ActorContext, ActorSystem}
import akka.persistence.pg.event._
import akka.persistence.pg.journal.WriteStrategy
import com.typesafe.config.{ConfigFactory, Config}
import org.postgresql.ds.PGSimpleDataSource
import slick.jdbc.JdbcBackend
import slick.util.AsyncExecutor

import scala.collection.JavaConverters._

object PluginConfig {
  def apply(system: ActorSystem) = new PluginConfig(system.settings.config)

  def apply(config: Config) = new PluginConfig(config)

  def asOption(s: String): Option[String] = if (s.isEmpty) None else Some(s)

  def newInstance[T](clazz: String): T = Thread.currentThread().getContextClassLoader.loadClass(clazz).asInstanceOf[Class[_ <: T]].newInstance()

}

class PluginConfig(systemConfig: Config) {
  private val config = systemConfig.getConfig("pg-persistence")

  val schema: Option[String] = PluginConfig.asOption(config.getString("schemaName"))
  val schemaName: String = schema.fold("")(n => '"' + n + '"')

  def getFullName(partialName: String): String = schema.fold(partialName)('"' + _ + '"' + '.' + partialName)

  val journalTableName: String = config.getString("journalTableName")
  val fullJournalTableName: String = getFullName(journalTableName)

  val snapshotTableName: String = config.getString("snapshotTableName")
  val fullSnapshotTableName: String = getFullName(snapshotTableName)

  val snapshotEncoder: JsonEncoder = PluginConfig.asOption(config.getString("snapshotEncoder"))
    .fold(NoneJsonEncoder: JsonEncoder)(PluginConfig.newInstance[JsonEncoder])

  def shutdownDataSource(): Unit = database.close()

  val jsonType: String = config.getString("pgjson")

  val pgPostgresProfile = new PgPostgresProfileImpl(jsonType match {
        case "jsonb"   => "jsonb"
        case "json"    => "json"
        case a: String => sys.error(s"unsupported value for pgjson '$a'. Only 'json' or 'jsonb' supported")
      })

  lazy val database: JdbcBackend.DatabaseDef = createDatabase

  lazy val dbConfig: Config = config.getConfig("db")

  lazy val numThreads: Int = dbConfig.getInt("numThreads")
  lazy val maxConnections: Int = if (dbConfig.hasPath("maxConnections")) dbConfig.getInt("maxConnections") else numThreads


  def createDatabase: JdbcBackend.DatabaseDef = {
    def asyncExecutor(name: String): AsyncExecutor = {
      AsyncExecutor(s"$name", numThreads, numThreads, dbConfig.getInt("queueSize"), maxConnections)
    }

    val db = PluginConfig.asOption(dbConfig.getString("jndiName")) match {
      case Some(jndiName) =>
        JdbcBackend.Database.forName(jndiName, Some(maxConnections), asyncExecutor(jndiName))

      case None           =>
        dbConfig.getString("connectionPool") match {

          case "disabled" =>
            val simpleDataSource = new PGSimpleDataSource()
            simpleDataSource.setUrl(dbConfig.getString("url"))
            simpleDataSource.setUser(dbConfig.getString("user"))
            simpleDataSource.setPassword(dbConfig.getString("password"))
            simpleDataSource.setPrepareThreshold(1)
            JdbcBackend.Database.forDataSource(simpleDataSource, None, asyncExecutor("akkapg-unpooled"))

          case _ =>
            //Slick's Database.forConfig does NOT use the 'url' when also configuring using a JDBC DataSource instead of a JDBC Driver class
            val props = new Properties()
            org.postgresql.Driver.parseURL(dbConfig.getString("url"), new Properties()).asScala foreach {
              case ("PGDBNAME", v) => props.put("databaseName", v)
              case ("PGHOST", v)   => props.put("serverName", v)
              case ("PGPORT", v)   => props.put("portNumber", v)
              case (k, v)          => props.put(k, v)
            }
            val urlConfig = ConfigFactory.parseProperties(props).atPath("properties")
            val sourceConfig = dbConfig.withFallback(urlConfig).withoutPath("url").atPath("akkapg-pooled")
            JdbcBackend.Database.forConfig("akkapg-pooled", sourceConfig)
        }

    }
    db
  }

  lazy val eventStoreConfig: EventStoreConfig = EventStoreConfig(config.getConfig("eventstore"),
    schema,
    journalTableName)

  lazy val eventStore: Option[EventStore] = {
    PluginConfig.asOption(eventStoreConfig.cfg.getString("class")) map { storeName =>
      val storeClazz = Thread.currentThread.getContextClassLoader.loadClass(storeName).asInstanceOf[Class[_ <: EventStore]]
      storeClazz.getConstructor(classOf[PluginConfig]).newInstance(this)
    }
  }

  def writeStrategy(context: ActorContext): WriteStrategy = {
    val clazz = config.getString("writestrategy")
    val writeStrategyClazz = Thread.currentThread().getContextClassLoader.loadClass(clazz).asInstanceOf[Class[_ <: WriteStrategy]]
    writeStrategyClazz.getConstructor(classOf[PluginConfig], classOf[ActorSystem]).newInstance(this, context.system)
  }

  lazy val idForQuery: String =
    if (config.getString("writestrategy") == "akka.persistence.pg.journal.RowIdUpdatingStrategy") "rowid"
    else "id"

  lazy val ignoreSnapshotDecodingFailure: Boolean =
    config.getBoolean("ignoreSnapshotDecodingFailure")


}

case class EventStoreConfig(cfg: Config,
                            schema: Option[String],
                            journalTableName: String) {
  val idColumnName: String = cfg.getString("idColumnName")
  val useView: Boolean = cfg.getBoolean("useView")

  val schemaName: Option[String] = if (useView) PluginConfig.asOption(cfg.getString("schemaName")) else schema

  val tableName: String = if (useView) cfg.getString("tableName") else journalTableName

  val eventEncoder: JsonEncoder = PluginConfig.asOption(cfg.getString("encoder"))
    .fold(NoneJsonEncoder: JsonEncoder)(PluginConfig.newInstance[JsonEncoder])

  val eventTagger: EventTagger = PluginConfig.asOption(cfg.getString("tagger")) match {
      case None => NotTagged
      case Some("default") => DefaultTagger
      case Some(clazz) => PluginConfig.newInstance[EventTagger](clazz)
    }

}
