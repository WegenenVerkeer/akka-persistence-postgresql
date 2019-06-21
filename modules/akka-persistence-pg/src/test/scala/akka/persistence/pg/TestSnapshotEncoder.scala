package akka.persistence.pg

import akka.persistence.pg.TestActor.TheState
import akka.persistence.pg.event.JsonEncoder

import scala.util.parsing.json._

class TestSnapshotEncoder extends JsonEncoder {

  override def toJson = {
    case a: TheState => JsonString(s"""{
                                  | "id": "${a.id}",
                                  | "count": ${a.count}
                                  |}""".stripMargin)
  }

  private def parseJsonString(jsonString: JsonString) =
    JSON.parseFull(jsonString.value).get.asInstanceOf[Map[String, Any]]

  override def fromJson = {
    case (json, c) if c == classOf[TheState] =>
      val jsValue = parseJsonString(json)
      TheState(jsValue("id").asInstanceOf[String], jsValue("count").asInstanceOf[Double].toInt)
  }

}
