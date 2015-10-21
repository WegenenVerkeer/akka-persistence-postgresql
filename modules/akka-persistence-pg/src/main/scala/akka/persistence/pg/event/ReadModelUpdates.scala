package akka.persistence.pg.event

import slick.dbio.DBIO

trait ReadModelUpdates {

  def readModelUpdates: Seq[DBIO[_]]

}
