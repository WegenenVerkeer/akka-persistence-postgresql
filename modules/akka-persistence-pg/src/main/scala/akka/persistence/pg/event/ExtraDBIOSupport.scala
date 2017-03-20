package akka.persistence.pg.event

import slick.dbio.DBIO

trait ExtraDBIOSupport {

  def extraDBIO: DBIO[_]
  def failureHandler: PartialFunction[Throwable, Unit]

}
