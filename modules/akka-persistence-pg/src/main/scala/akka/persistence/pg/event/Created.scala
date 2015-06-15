package akka.persistence.pg.event

import java.time.ZonedDateTime

trait Created {

  def created: ZonedDateTime

}
