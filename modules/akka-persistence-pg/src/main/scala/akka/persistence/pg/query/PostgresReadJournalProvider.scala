package akka.persistence.pg.query

import akka.actor.ExtendedActorSystem
import akka.persistence.query.ReadJournalProvider
import com.typesafe.config.Config

class PostgresReadJournalProvider(system: ExtendedActorSystem, config: Config) extends ReadJournalProvider {

  def scaladslReadJournal(): scaladsl.PostgresReadJournal = new scaladsl.PostgresReadJournal(system, config)

  def javadslReadJournal(): javadsl.PostgresReadJournal = new javadsl.PostgresReadJournal(scaladslReadJournal())
}
