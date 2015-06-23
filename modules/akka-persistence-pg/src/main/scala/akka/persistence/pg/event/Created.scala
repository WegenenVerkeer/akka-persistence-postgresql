package akka.persistence.pg.event

import java.time.OffsetDateTime

trait Created {

  def created: OffsetDateTime

}
