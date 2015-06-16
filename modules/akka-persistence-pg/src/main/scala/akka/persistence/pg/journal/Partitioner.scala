package akka.persistence.pg.journal

import scala.util.matching.Regex

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

class RegexPartitioner(regexp: Regex) extends Partitioner {

  override def partitionKey(persistenceId: String): Option[String] = {
    persistenceId match {
      case regexp(partitionKey) => Option(partitionKey)
      case _ => None
    }
  }

}

object DefaultRegexPartitioner extends RegexPartitioner("([^_]+.*".r)

