package akka.persistence.pg.event

import akka.persistence.pg.JsonString

trait JsonEncoder {

  /**
    * A partial function that serializes an event to a json representation
    * @return the json representation
    */
  def toJson: PartialFunction[Any, JsonString]

  /**
    * A partial function that deserializes an event from some json representation
    * @return the event
    */
  def fromJson: PartialFunction[(JsonString, Class[_]), AnyRef]

}

object NoneJsonEncoder extends JsonEncoder {

  override def toJson: PartialFunction[Any, JsonString] = PartialFunction.empty[Any, JsonString]

  override def fromJson: PartialFunction[(JsonString, Class[_]), AnyRef] =
    PartialFunction.empty[(JsonString, Class[_]), AnyRef]

}
