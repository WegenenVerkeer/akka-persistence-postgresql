package akka.persistence.pg

import akka.actor.Props
import akka.persistence.pg.TestActor._
import akka.persistence.pg.journal.query.PostgresReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.concurrent.Eventually

import scala.language.postfixOps

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


  private def startSource(tags: Set[EventTag], fromRowId: Long): Source[TestActor.Event, Unit] = {

    val readJournal =
      PersistenceQuery(system)
        .readJournalFor[PostgresReadJournal](PostgresReadJournal.Identifier)

    readJournal.eventsByTags(tags, fromRowId).map { env =>
      // and this will blow up if something different than a DomainEvent comes in!!
      env.event match {
        case evt: TestActor.Event => evt
        case unexpected => sys.error(s"Oeps!! That's was totally unexpected $unexpected")
      }
    }
  }


}