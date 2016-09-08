package gatlin

import io.gatling.core.session.Session
import io.gatling.commons.validation.Validation

/**
 * Created by peter on 01/10/15.
 */
trait AskMessage {
  def apply(session:Session): Validation[Any]
}



