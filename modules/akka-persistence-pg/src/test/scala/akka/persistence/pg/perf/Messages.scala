package akka.persistence.pg.perf

object Messages {
  case class Alter(text: String)
  case class Altered(text: String, created: Long)
}
