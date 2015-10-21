package akka.persistence.pg.event

trait Tagged[E] extends EventWrapper[E] {

  def tags: Map[String, String]

}
