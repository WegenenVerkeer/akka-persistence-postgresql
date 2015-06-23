package akka.persistence.pg.event

trait Tagged[E] {

  def tags: Map[String, String]
  def event: E

}
