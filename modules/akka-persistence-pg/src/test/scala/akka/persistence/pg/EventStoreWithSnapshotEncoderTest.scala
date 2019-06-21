package akka.persistence.pg

import akka.actor.Props
import akka.persistence.pg.TestActor._
import akka.persistence.pg.snapshot.SnapshotEntry
import com.typesafe.config.{Config, ConfigFactory}

class EventStoreWithSnapshotEncoderTest extends AbstractEventStoreTest {

  import driver.api._

  override lazy val config: Config = ConfigFactory.load("pg-eventstore-snapshotencoder.conf")

  test("generate snapshots as json") {
    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    testProbe.send(test, Alter("baz"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "baz"))

    testProbe.send(test, Snap)
    testProbe.expectMsg("s")

    database.run(events.size.result).futureValue shouldBe 1    //1 Alter event total
    database.run(snapshots.size.result).futureValue shouldBe 1 //1 snapshot stored
    val snapshotEntry: SnapshotEntry = database.run(snapshots.result.head).futureValue
    snapshotEntry.manifest shouldBe Some(classOf[TheState].getName)
    snapshotEntry.payload shouldBe None
    snapshotEntry.json.isDefined shouldBe true
    snapshotEntry.json.get.value shouldBe """{
                                            | "id": "baz",
                                            | "count": 0
                                            |}""".stripMargin

    database.run(journals.size.result).futureValue shouldBe 1 //1 journal message after the snapshot

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

  test("snapshots deserialization fails") {
    val test = system.actorOf(Props(new TestActor(testProbe.ref)))

    testProbe.send(test, Alter("baz"))
    testProbe.expectMsg("j")
    testProbe.send(test, GetState)
    testProbe.expectMsg(TheState(id = "baz"))

    testProbe.send(test, Snap)
    testProbe.expectMsg("s")

    database.run(events.size.result).futureValue shouldBe 1    //1 Alter event total
    database.run(snapshots.size.result).futureValue shouldBe 1 //1 snapshot stored

    val snapshotEntry: SnapshotEntry = database.run(snapshots.result.head).futureValue
    snapshotEntry.manifest shouldBe Some(classOf[TheState].getName)
    snapshotEntry.payload shouldBe None
    snapshotEntry.json.isDefined shouldBe true
    snapshotEntry.json.get.value shouldBe """{
                                            | "id": "baz",
                                            | "count": 0
                                            |}""".stripMargin

    // break serialized snapshot
    database
      .run(
        snapshots
          .filter(_.persistenceId === snapshotEntry.persistenceId)
          .update(snapshotEntry.copy(json = Some(JsonString("""{
                                                   | "id2": "bazz",
                                                   | "count": 0
                                                   |}""".stripMargin))))
      )
      .futureValue

    // kill the actor
    system.stop(test)
    testProbe watch test
    testProbe.expectTerminated(test)

    // get persisted state
    val test2 = system.actorOf(Props(new TestActor(testProbe.ref)))
    testProbe.send(test2, GetState)
    testProbe.expectMsg(TheState(id = "baz"))

    system.stop(test2)
    testProbe watch test2
    testProbe.expectTerminated(test2)
    ()
  }

}
