package be.wegenenverkeer.es.actor

import be.wegenenverkeer.es.domain.AggregateRoot

object Messages {

  def simpleName[AR <: AggregateRoot[_]](clazz: Class[AR]): String = {
    clazz.getSimpleName.replace("Aggregate", "").toLowerCase
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
