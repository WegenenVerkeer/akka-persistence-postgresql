package akka.persistence.pg

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import akka.persistence.pg.TestActor.{Incremented, Altered}
import akka.persistence.pg.event.JsonEncoder
import play.api.libs.json.{JsValue, JsNumber, JsString, JsObject}


class TestEventEncoder extends JsonEncoder {

  val A = classOf[Altered]
  val I = classOf[Incremented]

  override def toJson = {
    case a: Altered => JsObject(Seq("type" -> JsString("altered"),
      "id" -> JsString(a.id),
      "created" -> JsString(DateTimeFormatter.ISO_DATE_TIME.format(a.created))))
    case i: Incremented => JsObject(Seq("count" -> JsNumber(i.count),
      "created" -> JsString(DateTimeFormatter.ISO_DATE_TIME.format(i.created))))
  }

  def parseDateTime(json: JsValue): OffsetDateTime = {
    OffsetDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse((json \ "created").as[String]))
  }

  override def fromJson = {
    case (json, A) => Altered((json \ "id").as[String], parseDateTime(json))
    case (json, I) => Incremented((json \ "count").as[Int], parseDateTime(json))
  }

}