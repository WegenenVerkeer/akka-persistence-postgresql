package akka.persistence.pg.query

import akka.actor.Props
import akka.persistence.pg.TestActor._
import akka.persistence.pg._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import org.scalatest.concurrent.Eventually

import scala.language.postfixOps

/**
  * uses the default RowIdUpdating write strategy and will use the "rowid" column of the journal
  * table for queries
 */
class EventStoreQueryTest extends AbstractEventStoreTest with Eventually {

  implicit val materializer = ActorMaterializer()

  test("query tagged events (tagged with 'Altered')") {

    val test = system.actorOf(Props(new TestActor(testProbe.ref)))
    testProbe.send(test, Alter("foo"))
    testProbe.expectMsg("j")
    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, Increment(1))
    testProbe.expectMsg("j")

    val eventSource = startSource(Set(TestTags.alteredTag), 0)

    var events = List[TestActor.Event]()

    def checkSizeReceivedEvents(size: Int) = {
      eventually {
        events should have size size
      }
      val onlyAlteredEvents = events.collect { case evt: Altered => evt }
      onlyAlteredEvents should have size size
    }

    // a Sink that will append each event to the Events List
    val sink = Sink.foreach[TestActor.Event] { e =>
      events = events :+ e
    }

    eventSource.to(sink).run()

    checkSizeReceivedEvents(2)
    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, Increment(1))
    testProbe.expectMsg("j")
    checkSizeReceivedEvents(3)
  }

  test("query tagged events (tagged with 'Altered' or 'Incremented')") {

    val test = system.actorOf(Props(new TestActor(testProbe.ref)))
    testProbe.send(test, Alter("foo"))
    testProbe.expectMsg("j")
    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, Increment(1))
    testProbe.expectMsg("j")

    val eventSource = startSource(Set(TestTags.alteredTag, TestTags.incrementedTag), 0)

    var events = List[TestActor.Event]()

    def checkSizeReceivedEvents(size: Int) = {
      eventually {
        events should have size size
      }
    }

    // a Sink that will append each event to the Events List
    val sink = Sink.foreach[TestActor.Event] { e =>
      events = events :+ e
    }

    eventSource.to(sink).run()

    checkSizeReceivedEvents(3)
    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, Increment(1))
    testProbe.expectMsg("j")
    checkSizeReceivedEvents(5)
  }

  test("query all events") {

    val test = system.actorOf(Props(new TestActor(testProbe.ref)))
    testProbe.send(test, Alter("foo"))
    testProbe.expectMsg("j")
    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, Increment(1))
    testProbe.expectMsg("j")

    val eventSource = startSource(0)

    var events = List[TestActor.Event]()

    def checkSizeReceivedEvents(size: Int) = {
      eventually {
        events should have size size
      }
    }

    // a Sink that will append each event to the Events List
    val sink = Sink.foreach[TestActor.Event] { e =>
      events = events :+ e
    }

    eventSource.to(sink).run()

    checkSizeReceivedEvents(3)
    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, Increment(1))
    testProbe.expectMsg("j")
    checkSizeReceivedEvents(5)

  }

  test("query events by persistenceId") {

    val test = system.actorOf(Props(new TestActor(testProbe.ref, Some("TestActor"))))
    testProbe.send(test, Alter("foo"))
    testProbe.expectMsg("j")
    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, Increment(1))
    testProbe.expectMsg("j")

    val eventSource = startSource("TestActor", 0)

    var events = List[TestActor.Event]()

    def checkSizeReceivedEvents(size: Int) = {
      eventually {
        events should have size size
      }
    }

    // a Sink that will append each event to the Events List
    val sink = Sink.foreach[TestActor.Event] { e =>
      events = events :+ e
    }

    eventSource.to(sink).run()

    checkSizeReceivedEvents(3)
    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, Increment(1))
    testProbe.expectMsg("j")
    checkSizeReceivedEvents(5)

  }

}