package akka.persistence.pg

import java.util.concurrent.atomic.AtomicInteger

trait WaitForEvents {

  def waitUntilEventsWritten(expected: Int, written: AtomicInteger) = {
    var noProgressCount = 0
    var numEvents       = written.get()
    while (numEvents != expected && noProgressCount < 50) {
      Thread.sleep(100L)
      val numExtra = written.get() - numEvents
      if (numExtra == 0) noProgressCount += 1
      else numEvents += numExtra
    }
  }

}
