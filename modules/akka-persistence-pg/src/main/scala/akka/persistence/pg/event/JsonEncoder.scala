package akka.persistence.pg.event

import play.api.libs.json.{JsError, JsSuccess, JsValue, Reads}

trait JsonEncoder {

  def toJson: PartialFunction[Any, JsValue]

  def fromJson: PartialFunction[(JsValue, Class[_]), AnyRef]

  def tryRead[T](json: JsValue)(implicit reads: Reads[T]): T = {
    reads.reads(json) match {
      case JsSuccess(v, _) => v
      case JsError(e)      => sys.error(e.toString())
    }
  }

}

object NoneJsonEncoder extends JsonEncoder {

  override def toJson: PartialFunction[Any, JsValue] = PartialFunction.empty[Any, JsValue]

  override def fromJson: PartialFunction[(JsValue, Class[_]), AnyRef] = PartialFunction.empty[(JsValue, Class[_]), AnyRef]

}
