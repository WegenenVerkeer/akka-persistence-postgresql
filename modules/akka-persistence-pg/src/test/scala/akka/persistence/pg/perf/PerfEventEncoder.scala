package akka.persistence.pg.perf

import akka.persistence.pg.event.JsonEncoder
import akka.persistence.pg.perf.Messages.Altered
import play.api.libs.json.{JsObject, JsString}

class PerfEventEncoder extends JsonEncoder {

  override def toJson = {
    case Altered(text) => JsObject(Seq("type" -> JsString("altered"), "txt" -> JsString(text)))
  }

  override def fromJson = {
    case(json, _) => Altered((json \ "txt").as[String])
  }

}
