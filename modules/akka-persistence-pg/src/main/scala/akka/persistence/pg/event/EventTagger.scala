package akka.persistence.pg.event

trait EventTagger {

  /**
   *
   * @param persistenceId the persistenceId of the [[akka.persistence.PersistentActor]] calling the
   *                      persist(event) method.
   * @param event the event/message (argument of persist call)
   * @return a tuple containing the tags and the event to persist.
   */
  def tag(persistenceId: String, event: Any): Map[String, String]

}

object DefaultTagger extends EventTagger {

  override def tag(persistenceId: String, event: Any) = {
    event match {
      case t: Tagged => t.tags
      case _         => Map.empty
    }
  }
}