package akka.persistence.pg.util

import java.sql.{Connection, DatabaseMetaData}
import java.util.concurrent.CountDownLatch

import akka.actor.{ActorSystem, Props}
import slick.jdbc.JdbcBackend

import scala.language.postfixOps

/**
 * test utility for testing PersistentActors with akka-persistence-pg plugin:
 * Send your actors messages in a code block wrapped in a withTransactionRollback call
 * This will make sure that all persistent messages are stored within a DB transaction that will be rolled back automatically
 *
 * Important Remarks:
 *
 * make sure to configure akka-persistence to use the TestPgSyncWriteJournal class in your akka test config:
 * pg-journal.class = "akka.persistence.pg.journal.TestPgSyncWriteJournal"
 *
 * This class is not thread-safe and contains shared global state. Make sure to NOT run your tests in parallel
 *
 */
object PgPluginTestUtil {

  private[this] var dbLatch: CountDownLatch = new CountDownLatch(1)

  private[pg] var db: RollbackDatabase = _

  /**
   * Initialize the global state. This will be called when the TestPgSyncWriteJournal is instantiated by akka-persistence
   * @param db the database
   */
  private[pg] def initialize(db: JdbcBackend.DatabaseDef, actorSystem: ActorSystem): RollbackDatabase = {
    if (this.db == null) {
      this.db = new RollbackDatabase(db)
      dbLatch.countDown()
      actorSystem.registerOnTermination(uninitialize())
    }
    this.db
  }

  /**
   * Unitialize the global state. This will be called when the actor system stops
   */
  private[pg] def uninitialize() = {
    db = null
    dbLatch = new CountDownLatch(1)
  }

  /**
   * @param f function block to be called within a tx that will be rolled back
   * @return the result of the function call
   */
  def withTransactionRollback[T](f: JdbcBackend.DatabaseDef => T)
                                (implicit system: ActorSystem): T = {
    if (db == null) {
      //send a dummy message to start up akka-persistence (asynchronously) because akka-persistence only starts when
      //a message is sent for the first time to a persistent actor
      system.actorOf(Props(classOf[DummyPersistentActor])) ! DummyCommand
    }
    //wait until akka-persistence is initialized
    dbLatch.await()
    //create a new session that will eventually be rolled back
    db.newRollbackSession()
    try {
      f(db)
    } finally {
      db.rollbackAndClose()
    }
  }

  private class RollbackSession(override val database: JdbcBackend.DatabaseDef) extends JdbcBackend.Session {

    override val conn: Connection = database.source.createConnection()
    conn.setAutoCommit(false)

    override def capabilities: JdbcBackend.DatabaseCapabilities = new JdbcBackend.DatabaseCapabilities(this)

    override def metaData: DatabaseMetaData = conn.getMetaData

    override def close(): Unit = {
    }

    override def endInTransaction(f: => Unit): Unit = {}
    override def startInTransaction: Unit = {}

  }


  private[pg] class RollbackDatabase(database: JdbcBackend.DatabaseDef) extends JdbcBackend.DatabaseDef(database.source, database.executor) {

    private var session: Option[RollbackSession] = None

    def newRollbackSession(): Unit = {
      session = Option(new RollbackSession(database))
    }

    override def createSession(): JdbcBackend.SessionDef = {
      session.getOrElse(sys.error("make sure to call newRollbackSession first"))
    }

    def rollbackAndClose() = {
      session.foreach { s =>
        s.conn.rollback()
        s.conn.close()
      }
      session = None
    }

  }

}
