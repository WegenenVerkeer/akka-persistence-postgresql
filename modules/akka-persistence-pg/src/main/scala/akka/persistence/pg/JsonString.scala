package akka.persistence.pg

/**
  * a simple wrapper around a json string representation
  * @param value the wrapped json string
  */
case class JsonString(value: String)
