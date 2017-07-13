package akka.persistence.pg

import java.time.format.DateTimeFormatter

import akka.actor.Props
import akka.persistence.pg.TestActor._

import scala.util.parsing.json.JSON

class EventStoreTest extends AbstractEventStoreTest {

  import driver.api._

  test("generate events") {
    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    testProbe.send(test, Alter("foo"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "foo"))

    testProbe.send(test, Alter("bar"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "bar"))

    database.run(journals.size.result).futureValue shouldBe 2

    // kill the actor
    system.stop(test)
    testProbe watch test
    testProbe.expectTerminated(test)

    // get persisted state
    val test2 = system.actorOf(Props(new TestActor(testProbe.ref)))
    testProbe.send(test2, GetState)
    testProbe.expectMsg(TheState(id = "bar"))

    system.stop(test2)
    testProbe watch test2
    testProbe.expectTerminated(test2)
    ()
  }

  test("events implementing created use this as event's creation time") {
    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    testProbe.send(test, Alter("foo"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "foo"))

    database.run(events.size.result).futureValue shouldBe 1
    val storedEvent = database.run(events.result.head).futureValue
    getCreated(storedEvent.event) shouldBe  DateTimeFormatter.ISO_DATE_TIME.format(storedEvent.created)
  }

  //put on ignore because the assertion can NOT be guaranteed, the timestamps could very well be the same
  ignore("events NOT implementing created don't use this as event's creation time") {
    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    testProbe.send(test, Increment(5))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(count = 5))

    database.run(events.size.result).futureValue shouldBe 1
    val storedEvent = database.run(events.result.head).futureValue
    getCreated(storedEvent.event) shouldNot  be(DateTimeFormatter.ISO_DATE_TIME.format(storedEvent.created))
  }

  def getCreated(jsonString: JsonString): Any = {
    JSON.parseFull(jsonString.value).get.asInstanceOf[Map[String, Any]]("created")
  }

  test("generate snapshots") {
    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    testProbe.send(test, Alter("baz"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "baz"))

    testProbe.send(test, Snap)
    testProbe.expectMsg("s")

    database.run(events.size.result).futureValue shouldBe 1    //1 Alter event total
    database.run(snapshots.size.result).futureValue shouldBe 1 //1 snapshot stored
    database.run(journals.size.result).futureValue shouldBe 1  //1 journal message after the snapshot

    testProbe.send(test, Alter("foobar"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "foobar"))

    database.run(events.size.result).futureValue shouldBe 2    //2 Alter events total
    database.run(snapshots.size.result).futureValue shouldBe 1 //1 snapshot stored
    database.run(journals.size.result).futureValue shouldBe 2  //2 journal message

    // kill the actor
    system.stop(test)
    testProbe watch test
    testProbe.expectTerminated(test)

    // get persisted state
    val test2 = system.actorOf(Props(new TestActor(testProbe.ref)))
    testProbe.send(test2, GetState)
    testProbe.expectMsg(TheState(id = "foobar"))

    system.stop(test2)
    testProbe watch test2
    testProbe.expectTerminated(test2)
    ()
  }

}



