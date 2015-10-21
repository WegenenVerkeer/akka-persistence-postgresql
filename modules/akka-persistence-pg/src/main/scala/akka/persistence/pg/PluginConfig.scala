package akka.persistence.pg

import java.util.Properties
import java.util.concurrent.TimeUnit

import akka.actor.{ActorContext, ActorSystem}
import akka.persistence.pg.event._
import akka.persistence.pg.journal.{WriteStrategy, DefaultRegexPartitioner, NotPartitioned, Partitioner}
import com.typesafe.config.{ConfigFactory, Config}
import org.postgresql.ds.PGSimpleDataSource
import slick.jdbc.JdbcBackend
import slick.util.AsyncExecutor

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

object PluginConfig {
  def apply(system: ActorSystem) = new PluginConfig(system.settings.config)

  def apply(config: Config) = new PluginConfig(config)

  def asOption(s: String): Option[String] = {
    if (s.isEmpty) None else Some(s)
  }
}

class PluginConfig(systemConfig: Config) {

  private[this] val config = systemConfig.getConfig("pg-persistence")

  val schema: Option[String] = PluginConfig.asOption(config.getString("schemaName"))
  val schemaName = schema.fold("")(n => '"' + n + '"')

  def getFullName(partialName: String) = schema match {
    case None => partialName
    case Some(s) => '"' + s + '"' + '.' + partialName
  }

  val journalTableName = config.getString("journalTableName")
  val fullJournalTableName: String = getFullName(journalTableName)

  val rowIdSequenceName: String = s"${journalTableName}_rowid_seq"
  val fullRowIdSequenceName: String = getFullName(rowIdSequenceName)

  val snapshotTableName = config.getString("snapshotTableName")
  val fullSnapshotTableName: String = getFullName(snapshotTableName)

  def shutdownDataSource() = {
    database.close()
  }

  val jsonType = config.getString("pgjson")

  val pgPostgresDriver = new PgPostgresDriverImpl(jsonType match {
        case "jsonb"   => "jsonb"
        case "json"    => "json"
        case a: String => sys.error(s"unsupported value for pgjson '$a'. Only 'json' or 'jsonb' supported")
      })

  lazy val database = createDatabase

  def createDatabase = {
    val dbConfig = config.getConfig("db")

    def asyncExecutor(name: String): AsyncExecutor = {
      AsyncExecutor(s"$name executor", dbConfig.getInt("numThreads"), dbConfig.getInt("queueSize"))
    }

    val db = PluginConfig.asOption(dbConfig.getString("jndiName")) match {
      case Some(jndiName) =>
        JdbcBackend.Database.forName(jndiName, asyncExecutor(jndiName))

      case None           =>
        dbConfig.getString("connectionPool") match {

          case "disabled" =>
            val simpleDataSource = new PGSimpleDataSource()
            simpleDataSource.setUrl(dbConfig.getString("url"))
            simpleDataSource.setUser(dbConfig.getString("user"))
            simpleDataSource.setPassword(dbConfig.getString("password"))
            simpleDataSource.setPrepareThreshold(1)
            JdbcBackend.Database.forDataSource(simpleDataSource, asyncExecutor("unpooled"))

          case _ =>
            //Slick's Database.forConfig does NOT use the 'url' when also configuring using a JDBC DataSource instead of
            // a JDBC Driver class
            val props = new Properties()
            org.postgresql.Driver.parseURL(dbConfig.getString("url"), new Properties()).asScala foreach {
              case ("PGDBNAME", v) => props.put("databaseName", v)
              case ("PGHOST", v)   => props.put("serverName", v)
              case ("PGPORT", v)   => props.put("portNumber", v)
              case (k, v)          => props.put(k, v)
            }
            val urlConfig = ConfigFactory.parseProperties(props).atPath("properties")
            JdbcBackend.Database.forConfig("", dbConfig.withFallback(urlConfig))
        }

    }
    db
  }

  lazy val eventStoreConfig = new EventStoreConfig(config.getConfig("eventstore"),
    schema,
    journalTableName)

  lazy val eventStore: Option[EventStore] = {
    PluginConfig.asOption(eventStoreConfig.cfg.getString("class")) map { storeName =>
      val storeClazz = Thread.currentThread.getContextClassLoader.loadClass(storeName).asInstanceOf[Class[_ <: EventStore]]
      storeClazz.getConstructor(classOf[PluginConfig]).newInstance(this)
    }
  }

  lazy val journalPartitioner: Partitioner = PluginConfig.asOption(config.getString("partitioner")) match {
    case None => NotPartitioned
    case Some("default") => DefaultRegexPartitioner
    case Some(clazz) => Thread.currentThread.getContextClassLoader.loadClass(clazz).asInstanceOf[Class[_ <: Partitioner]].newInstance()
  }

  lazy val snapshotDeleteAwaitDuration: FiniteDuration = {
    val duration = config.getDuration("snapshotDeleteAwaitDuration")
    FiniteDuration(duration.toMillis, TimeUnit.MILLISECONDS)
  }

  def writeStrategy(context: ActorContext): WriteStrategy = {
    val clazz = config.getString("writestrategy")
    val writeStrategyClazz = Thread.currentThread().getContextClassLoader.loadClass(clazz).asInstanceOf[Class[_ <: WriteStrategy]]
    writeStrategyClazz.getConstructor(classOf[PluginConfig], classOf[ActorSystem]).newInstance(this, context.system)
  }

}

case class EventStoreConfig(cfg: Config,
                            schema: Option[String],
                            journalTableName: String) {

  val idColumnName: String = cfg.getString("idColumnName")
  val useView: Boolean = cfg.getBoolean("useView")

  val schemaName: Option[String] = if (useView) {
    PluginConfig.asOption(cfg.getString("schemaName"))
  } else {
    schema
  }

  val tableName: String = if (useView) {
    cfg.getString("tableName")
  } else {
    journalTableName
  }

  val eventEncoder: JsonEncoder = {
    PluginConfig.asOption(cfg.getString("encoder")) match {
      case None => NoneJsonEncoder
      case Some(clazz) => Thread.currentThread().getContextClassLoader.loadClass(clazz).asInstanceOf[Class[_ <: JsonEncoder]].newInstance()
    }
  }

  val eventTagger: EventTagger = {
    PluginConfig.asOption(cfg.getString("tagger")) match {
      case None => DefaultTagger
      case Some(clazz) => Thread.currentThread().getContextClassLoader.loadClass(clazz).asInstanceOf[Class[_ <: EventTagger]].newInstance()
    }
  }


}
