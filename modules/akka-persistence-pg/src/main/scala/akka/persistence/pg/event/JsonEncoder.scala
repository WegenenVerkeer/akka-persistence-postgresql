package akka.persistence.pg.event

import akka.persistence.pg.JsonString

trait JsonEncoder {

  def toJson: PartialFunction[Any, JsonString]

  def fromJson: PartialFunction[(JsonString, Class[_]), AnyRef]

}

object NoneJsonEncoder extends JsonEncoder {

  override def toJson: PartialFunction[Any, JsonString] = PartialFunction.empty[Any, JsonString]

  override def fromJson: PartialFunction[(JsonString, Class[_]), AnyRef] = PartialFunction.empty[(JsonString, Class[_]), AnyRef]

}
