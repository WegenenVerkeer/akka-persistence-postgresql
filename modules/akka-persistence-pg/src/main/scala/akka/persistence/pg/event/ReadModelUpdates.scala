package akka.persistence.pg.event

import slick.dbio.DBIO

trait ReadModelUpdates[E] extends EventWrapper[E] {

  def readModelUpdates: Seq[DBIO[_]]

}
