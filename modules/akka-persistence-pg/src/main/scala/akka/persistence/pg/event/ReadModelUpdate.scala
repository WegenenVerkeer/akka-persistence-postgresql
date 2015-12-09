package akka.persistence.pg.event

import slick.dbio.DBIO

trait ReadModelUpdate {

  def readModelAction: DBIO[_]
  def failureHandler: PartialFunction[Throwable, Unit]

}
