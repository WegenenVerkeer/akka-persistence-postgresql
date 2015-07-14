package akka.persistence.pg

import java.util.Properties

import akka.actor.ActorSystem
import akka.persistence.pg.event._
import akka.persistence.pg.journal.{DefaultRegexPartitioner, NotPartitioned, Partitioner}
import com.typesafe.config.{ConfigFactory, Config}
import org.postgresql.ds.PGSimpleDataSource
import slick.jdbc.JdbcBackend
import slick.util.AsyncExecutor

import scala.collection.JavaConverters._

object PluginConfig {
  def apply(system: ActorSystem) = new PluginConfig(system)

  def asOption(s: String): Option[String] = {
    if (s.isEmpty) None else Some(s)
  }
}

class PluginConfig(system: ActorSystem) {

  private[this] val config = system.settings.config.getConfig("pg-persistence")

  val journalSchemaName: Option[String] = PluginConfig.asOption(config.getString("journalSchemaName"))
  val journalTableName = config.getString("journalTableName")

  val snapshotSchemaName: Option[String] = PluginConfig.asOption(config.getString("snapshotSchemaName"))
  val snapshotTableName = config.getString("snapshotTableName")

  def shutdownDataSource() = {
    database.close()
  }

  lazy val database = {
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
    journalSchemaName,
    journalTableName)

  lazy val eventStore: Option[EventStore] = {
    PluginConfig.asOption(eventStoreConfig.cfg.getString("class")) map { storeName =>
      val storeClazz = Thread.currentThread.getContextClassLoader.loadClass(storeName).asInstanceOf[Class[_ <: EventStore]]
      storeClazz.getConstructor(classOf[JdbcBackend.Database], classOf[EventStoreConfig]).newInstance(database, eventStoreConfig)
    }
  }

  lazy val journalPartitioner: Partitioner = PluginConfig.asOption(config.getString("partitioner")) match {
    case None => NotPartitioned
    case Some("default") => DefaultRegexPartitioner
    case Some(clazz) => Thread.currentThread.getContextClassLoader.loadClass(clazz).asInstanceOf[Class[_ <: Partitioner]].newInstance()
  }

}

case class EventStoreConfig(cfg: Config,
                            journalSchemaName: Option[String],
                            journalTableName: String) {

  val useView: Boolean = cfg.getBoolean("useView")

  val schemaName: Option[String] = if (useView) {
    PluginConfig.asOption(cfg.getString("schemaName"))
  } else {
    journalSchemaName
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
