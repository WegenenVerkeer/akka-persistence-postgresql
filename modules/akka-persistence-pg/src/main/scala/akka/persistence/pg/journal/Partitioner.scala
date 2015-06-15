package akka.persistence.pg.journal

trait Partitioner {

  /**
   *
   * @param persistenceId the persistenceId of the [[akka.persistence.PersistentActor]] calling the
   *                      persist(event) method.
   * @return the optional partition key
   *
   */
  def partitionKey(persistenceId: String): Option[String]

}

object NotPartitioned extends Partitioner {

  override def partitionKey(persistenceId: String) = None

}

object SimplePartitioned extends Partitioner {

  val extractPartitionKeyRe = "([^_]+.*".r

  override def partitionKey(persistenceId: String): Option[String] = {
    persistenceId match {
      case extractPartitionKeyRe(partitionKey) => Option(partitionKey)
      case _ => None
    }
  }
}