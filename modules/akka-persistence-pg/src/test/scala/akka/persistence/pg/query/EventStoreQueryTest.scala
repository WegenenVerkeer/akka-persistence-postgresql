package akka.persistence.pg.query

import akka.actor.Props
import akka.persistence.pg.TestActor._
import akka.persistence.pg._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually

import scala.concurrent.Future

/**
  * uses the default RowIdUpdating write strategy and will use the "rowid" column of the journal
  * table for queries
  */
class EventStoreQueryTest extends AbstractEventStoreTest with Eventually {

  override lazy val config = ConfigFactory.load("pg-eventstore-rowid.conf")

  implicit val materializer = ActorMaterializer()

  test("query tagged events (tagged with 'Altered')") {

    val test = system.actorOf(Props(new TestActor(testProbe.ref)))
    testProbe.send(test, Alter("foo"))
    testProbe.expectMsg("j")
    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, Increment(1))
    testProbe.expectMsg("j")

    val eventSource = startSource[TestActor.Event](Set(TestTags.alteredTag), 0)

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

    val eventSource = startSource[TestActor.Event](Set(TestTags.alteredTag, TestTags.incrementedTag), 0)

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

    val eventSource = startSource[TestActor.Event](0)

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

    val eventSource = startSource[TestActor.Event]("TestActor", 0)

    val test = system.actorOf(Props(new TestActor(testProbe.ref, Some("TestActor"))))
    testProbe.send(test, Alter("foo"))
    testProbe.expectMsg("j")
    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, Increment(1))
    testProbe.expectMsg("j")

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

  test("query current tagged events (tagged with 'Altered')") {

    val eventSource = startCurrentSource[TestActor.Event](Set(TestTags.alteredTag), 0)

    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    1 to 500 foreach {index =>
      if(index % 50 == 0) testProbe.send(test, Increment(1))
      else testProbe.send(test, Alter("foo-" + index))
      testProbe.expectMsg("j")
    }

    // wait until rowids are updated
    PgExtension(system).whenDone(Future.successful(())).futureValue

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

    checkSizeReceivedEvents(490)
    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, Increment(1))
    testProbe.expectMsg("j")
    checkSizeReceivedEvents(490)
  }

  test("query current tagged events (tagged with 'Altered' or 'Incremented')") {

    val eventSource = startCurrentSource[TestActor.Event](Set(TestTags.alteredTag, TestTags.incrementedTag), 0)

    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    1 to 500 foreach { index =>
      if(index % 2 == 0) testProbe.send(test, Increment(1))
      else testProbe.send(test, Alter("foo-" + index))
      testProbe.expectMsg("j")
    }

    // wait until rowids are updated
    PgExtension(system).whenDone(Future.successful(())).futureValue

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

    checkSizeReceivedEvents(500)
    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, Increment(1))
    testProbe.expectMsg("j")
    checkSizeReceivedEvents(500)
  }

  test("query current all events") {

    val eventSource = startCurrentSource[TestActor.Event](0)

    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    1 to 500 foreach { index =>
      testProbe.send(test, Alter(s"foo-$index"))
      testProbe.expectMsg("j")
    }

    // wait until rowids are updated
    PgExtension(system).whenDone(Future.successful(())).futureValue

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

    checkSizeReceivedEvents(500)
    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, Increment(1))
    testProbe.expectMsg("j")
    checkSizeReceivedEvents(500)

  }

  test("query current events by persistenceId") {

    val eventSource = startCurrentSource[TestActor.Event]("TestActor", 0)

    val test = system.actorOf(Props(new TestActor(testProbe.ref, Some("TestActor"))))

    1 to 500 foreach {index =>
      testProbe.send(test, Alter("foo-" + index))
      testProbe.expectMsg("j")
    }

    // wait until rowids are updated
    PgExtension(system).whenDone(Future.successful(())).futureValue

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

    checkSizeReceivedEvents(500)
    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, Increment(1))
    testProbe.expectMsg("j")
    checkSizeReceivedEvents(500)

  }

}
