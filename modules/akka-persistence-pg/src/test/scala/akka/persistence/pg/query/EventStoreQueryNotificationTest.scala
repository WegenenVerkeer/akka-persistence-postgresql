package akka.persistence.pg.query

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.{ActorRef, Props}
import akka.persistence.pg.TestActor._
import akka.persistence.pg._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{RunnableGraph, Sink}
import akka.util.Timeout
import org.scalatest.concurrent.{ScalaFutures, Eventually}
import org.scalatest.time.{Milliseconds, Seconds, Span}

import scala.language.postfixOps
import scala.util.Random

/**
  * uses the default RowIdUpdating write strategy and will use the "rowid" column of the journal
  * table for queries
 */
class EventStoreQueryNotificationTest extends AbstractEventStoreTest
  with Eventually
  with ScalaFutures {

  //override lazy val config = ConfigFactory.load("pg-eventstore-locking.conf")

  override implicit val patienceConfig = PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Milliseconds))

  implicit val materializer = ActorMaterializer()
  implicit val timeOut = Timeout(1, TimeUnit.MINUTES)

  val expected = 2000
  val numActors = 100
  var actors: Map[String, ActorRef] = Map.empty

  test("query all events") {
    var events = List[TestActor.Event]()
    val sink = Sink.foreach[TestActor.Event] { e =>
      events = events :+ e
    }

    val graph: RunnableGraph[NotUsed] = startSource(0).to(sink)

    1 to expected foreach { i =>
      actors.values.toSeq(Random.nextInt(actors.size)) ! Alter(i.toString)
    }

    graph.run()

    eventually {
      println(events.size)
      events should have size expected
    }

  }

  test("query persistenceId events") {
    var events = List[TestActor.Event]()
    val sink = Sink.foreach[TestActor.Event] { e =>
      events = events :+ e
    }

    var expectedForPersistenceId = 0
    val index = Random.nextInt(actors.size)
    val persistenceId = actors.keys.toSeq(index)
    val graph: RunnableGraph[NotUsed] = startSource(persistenceId, 0).to(sink)

    1 to expected foreach { i =>
      val chosen = Random.nextInt(actors.size)
      if (chosen == index) expectedForPersistenceId += 1
      actors.values.toSeq(chosen) ! Alter(i.toString)
    }

    graph.run()

    eventually {
      println(events.size)
      events should have size expectedForPersistenceId
    }
    database.run(countEvents(persistenceId)).futureValue shouldEqual expectedForPersistenceId

  }

  test("query tagged events tagged with 'Altered'") {
    var events = List[TestActor.Event]()
    val sink = Sink.foreach[TestActor.Event] { e =>
      events = events :+ e
    }

    val graph: RunnableGraph[NotUsed] =  startSource(Set(TestTags.alteredTag), 0).to(sink)

    1 to expected foreach { i =>
      actors.values.toSeq(Random.nextInt(actors.size)) ! Alter(i.toString)
    }

    graph.run()

    eventually {
      println(events.size)
      events should have size expected
    }

  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    actors = (1 to numActors map { i: Int =>
      val pid = s"TestActor-$i"
      pid -> system.actorOf(Props(new TestActor(testProbe.ref, Some(pid))))
    }).toMap
  }

}