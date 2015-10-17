package akka.persistence.pg.event

trait EventWrapper[E] {

  def event: E

}
