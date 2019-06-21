package akka.persistence.pg.perf

import akka.persistence.pg.JsonString
import akka.persistence.pg.event.JsonEncoder
import akka.persistence.pg.perf.Messages.Altered

import scala.util.parsing.json.JSON

class PerfEventEncoder extends JsonEncoder {

  override def toJson = {
    case Altered(text, created) => JsonString(s"""{
                                                  | "type": "altered",
                                                  | "txt": "$text",
                                                  | "created": "$created"
                                                  |}""".stripMargin)
  }

  private def parseJsonString(jsonString: JsonString) =
    JSON.parseFull(jsonString.value).get.asInstanceOf[Map[String, Any]]

  private def altered(jsValue: Map[String, Any]): Altered =
    Altered(jsValue("txt").asInstanceOf[String], jsValue("created").asInstanceOf[Long])

  override def fromJson = {
    case (json, _) => altered(parseJsonString(json))
  }

}
