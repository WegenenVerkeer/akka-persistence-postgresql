package akka.persistence.pg.event

import slick.dbio.DBIO

trait ReadModelUpdate {

  def readModelAction: DBIO[_]

}
