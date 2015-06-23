package akka.persistence.pg.event

trait EventTagger {

  /**
   *
   * @param persistenceId the persistenceId of the [[akka.persistence.PersistentActor]] calling the
   *                      persist(event) method.
   * @param event the event/message (argument of persist call)
   * @return a tuple containing the tags and the event to persist.
   */
  def tag(persistenceId: String, event: Any): (Map[String, String], Any)

}

object DefaultTagger extends EventTagger {

  override def tag(persistenceId: String, event: Any) = {
    event match {
      case t: Tagged[_] => (t.tags, t.event)
      case _            => (Map.empty, event)
    }
  }
}