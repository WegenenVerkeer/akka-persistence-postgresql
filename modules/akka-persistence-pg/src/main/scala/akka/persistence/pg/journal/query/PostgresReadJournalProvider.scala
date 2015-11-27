package akka.persistence.pg.journal.query

import akka.actor.ExtendedActorSystem
import akka.persistence.query.{scaladsl, ReadJournalProvider}
import akka.persistence.query.javadsl.ReadJournal
import com.typesafe.config.Config

class PostgresReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {

  def scaladslReadJournal(): scaladsl.ReadJournal = new PostgresReadJournal(system, config)

  def javadslReadJournal(): ReadJournal = new ReadJournal {}
}
