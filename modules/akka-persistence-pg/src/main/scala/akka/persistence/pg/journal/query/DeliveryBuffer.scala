package akka.persistence.pg.journal.query

import akka.actor.ActorLogging
import akka.stream.actor.ActorPublisher


/**
 * INTERNAL API
 */
private[akka] trait DeliveryBuffer[T] {
  _: ActorPublisher[T] with ActorLogging =>

  var buf = Vector.empty[T]

  def deliverBuf(): Unit = deliverBuf(totalDemand)

  def deliverBuf(demand: Long): Unit =
    if (buf.nonEmpty && demand > 0) {
      if (buf.size == 1) {
        // optimize for this common case
        onNextWithLogging(buf.head)
        buf = Vector.empty
      } else if (demand <= Int.MaxValue) {
        val (use, keep) = buf.splitAt(demand.toInt)
        buf = keep
        use foreach onNextWithLogging
      } else {
        buf foreach onNextWithLogging
        buf = Vector.empty
      }
    }

  def onNextWithLogging(element: T): Unit = {
    log.debug(s"sending event $element")
    onNext(element)
  }
}
