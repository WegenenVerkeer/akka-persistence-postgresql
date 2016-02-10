package akka.persistence.pg.perf

import akka.persistence.pg.event.JsonEncoder
import akka.persistence.pg.perf.Messages.Altered
import play.api.libs.json.{JsNumber, JsObject, JsString}

class PerfEventEncoder extends JsonEncoder {

  override def toJson = {
    case Altered(text, created) => JsObject(Seq("type" -> JsString("altered"), "txt" -> JsString(text), "created" -> JsNumber(created)))
  }

  override def fromJson = {
    case(json, _) => Altered((json \ "txt").as[String], (json \ "created").as[Long])
  }

}
