package akka.persistence.pg

import akka.persistence.pg.event.EventTagger
import TestActor._

class TestEventTagger extends EventTagger {

  def tag(persistenceId: String, event: Any): (Map[String, String], Any) = {
    event match {
      case evt: Altered => (Map(TestTags.alteredTag), event)
      case evt: Incremented => (Map(TestTags.incrementedTag), event)
      case other => (Map.empty, other)
    }
  }
}

object TestTags {
  val alteredTag = "_type" -> "Altered"
  val incrementedTag = "_type" -> "Incremented"
}