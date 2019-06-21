package akka.persistence.pg.journal

class Notifier(entries: Seq[JournalEntry], writeJournal: PgAsyncWriteJournal) {

  def eventsAvailable(): Unit =
    writeJournal.notifyEventsAvailable(entries)

}
