package akka.persistence.pg

import akka.persistence.pg.event.EventTagger
import TestActor._

class TestEventTagger extends EventTagger {

  def tag(event: Any): Map[String, String] = {
    event match {
      case evt: Altered => Map(TestTags.alteredTag)
      case evt: Incremented => Map(TestTags.incrementedTag)
      case _ => Map.empty
    }
  }
}

object TestTags {
  val alteredTag = "_type" -> "Altered"
  val incrementedTag = "_type" -> "Incremented"
}