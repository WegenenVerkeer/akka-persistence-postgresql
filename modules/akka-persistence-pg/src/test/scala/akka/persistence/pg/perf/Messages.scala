package akka.persistence.pg.perf

object Messages {
  sealed trait Event
  case class Alter(text: String)
  case class Altered(text: String, created: Long) extends Event
}
