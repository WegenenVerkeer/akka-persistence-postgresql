package be.wegenenverkeer.es.actor

import be.wegenenverkeer.es.domain.AggregateRoot
import play.api.libs.json.{JsObject, Json}

object Messages {

  def simpleName[AR <: AggregateRoot[_]](clazz: Class[AR]): String = {
    clazz.getSimpleName.replace("Aggregate", "").toLowerCase
  }

  def jsonErrorMessage(errorMessage: String): JsObject = {
    Json.obj("status" -> "KO", "message" -> errorMessage)
  }

}

case class Error(message: String)

case class Acknowledge(id: String)

case class AggregateNotFound[AR <: AggregateRoot[_]](clazz: Class[AR], id: String) {

  def errorMessage: String = {
    s"${Messages.simpleName(clazz)} met id = '$id' bestaat niet"
  }

}

case class AggregateAlreadyExists[AR <: AggregateRoot[_]](clazz: Class[AR], id: String) {

  def errorMessage: String = {
    s"${Messages.simpleName(clazz)} met id = '$id' bestaat reeds"
  }

}
