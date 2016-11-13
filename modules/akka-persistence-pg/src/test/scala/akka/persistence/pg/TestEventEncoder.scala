package akka.persistence.pg

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import akka.persistence.pg.TestActor.{Incremented, Altered}
import akka.persistence.pg.event.JsonEncoder
import scala.util.parsing.json._

class TestEventEncoder extends JsonEncoder {

  val A = classOf[Altered]
  val I = classOf[Incremented]

  override def toJson = {
    case a: Altered => JsonString(s"""{
                                  | "type": "altered",
                                  | "id": "${a.id}",
                                  | "created": "${DateTimeFormatter.ISO_DATE_TIME.format(a.created)}"
                                  |}""".stripMargin)
    case i: Incremented => JsonString(s"""{
                                          | "count": ${i.count},
                                          | "created": "${DateTimeFormatter.ISO_DATE_TIME.format(i.created)}"
                                          |}""".stripMargin)
  }

  private def parseDateTime(jsonMap: Map[String, Any]): OffsetDateTime = {
    OffsetDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse(jsonMap("created").asInstanceOf[String]))
  }

  private def altered(jsValue: Map[String, Any]): Altered = {
    Altered(jsValue("id").asInstanceOf[String], parseDateTime(jsValue))
  }

  private def incremented(jsValue: Map[String, Any]): Incremented = {
    Incremented(jsValue("count").asInstanceOf[Double].toInt, parseDateTime(jsValue))
  }

  private def parseJsonString(jsonString: JsonString) = {
    JSON.parseFull(jsonString.value).get.asInstanceOf[Map[String, Any]]
  }

  override def fromJson = {
    case (json, A) => altered(parseJsonString(json))
    case (json, I) => incremented(parseJsonString(json))
  }

}