package akka.persistence.pg.event

trait Tagged {

  def tags: Map[String, String]

}
