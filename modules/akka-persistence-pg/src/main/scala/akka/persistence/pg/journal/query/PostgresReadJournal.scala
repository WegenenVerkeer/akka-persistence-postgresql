package akka.persistence.pg.journal.query

import java.net.URLEncoder

import akka.actor.ExtendedActorSystem
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.Config
import scala.concurrent.duration._
import akka.persistence.pg.EventTag

class PostgresReadJournal(system: ExtendedActorSystem, config: Config) extends ReadJournal with EventsByTags {


  private val refreshInterval = config.getDuration("refresh-interval", MILLISECONDS).millis
  private val writeJournalPluginId: String = config.getString("write-plugin")
  private val maxBufSize: Int = config.getInt("max-buffer-size")

  def eventsByTags(tags: Set[EventTag], fromRowId: Long, toRowId: Long = Long.MaxValue): Source[EventEnvelope, Unit] = {
    Source.actorPublisher[EventEnvelope](
      EventsByTagsPublisher.props(
        tags = tags,
        fromOffset = fromRowId,
        toOffset = toRowId,
        refreshInterval = refreshInterval,
        maxBufSize = maxBufSize,
        writeJournalPluginId = writeJournalPluginId
      )
    ).mapMaterializedValue(_ => ())
      .named("eventsByTags-" + URLEncoder.encode(tags.mkString("-"), ByteString.UTF_8))

  }


}

object PostgresReadJournal {

  /**
   * The default identifier for [[PostgresReadJournal]] to be used with
   * [[akka.persistence.query.PersistenceQuery#readJournalFor]].
   *
   * The value is `"akka.persistence.pg.journal.query"` and corresponds
   * to the absolute path to the read journal configuration entry.
   */
  final val Identifier = "akka.persistence.pg.journal.query"

}