package akka.persistence.pg.query

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.ActorRef
import akka.persistence.pg._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{RunnableGraph, Sink}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Milliseconds, Seconds, Span}

import scala.util.Random

/**
  * uses the RowIdUpdating write strategy and will use the "rowid" column of the journal
  * table for queries
  */
class EventStoreQueryNotificationTest extends AbstractEventStoreTest with PgConfig with Eventually with ScalaFutures {

  override lazy val config: Config = ConfigFactory.load("pg-eventstore-rowid.conf")

  override implicit val patienceConfig = PatienceConfig(timeout = Span(20, Seconds), interval = Span(100, Milliseconds))

  implicit val materializer = ActorMaterializer()
  implicit val timeOut      = Timeout(1, TimeUnit.MINUTES)

  val expected                      = 2000
  val numActors                     = 100
  var actors: Map[String, ActorRef] = Map.empty

  test("query tagged events tagged with 'Altered'") {
    var events = List[E]()
    val sink = Sink.foreach[E] { e =>
      events = events :+ e
    }

    val graph: RunnableGraph[NotUsed] = startSource[E](Set(TestTags.alteredTag), 0).to(sink)

    1 to expected foreach { i =>
      actors.values.toSeq(Random.nextInt(actors.size)) ! alterCommand(i)
    }

    graph.run()

    println(s"query tagged events, expecting $expected events")
    eventually {
      println(events.size)
      if (events.size >= expected - 5) checkConsecutive(events)
      events.size shouldBe expected
    }

  }

  test("query all events") {
    var events = List[E]()
    val sink = Sink.foreach[E] { e =>
      events = events :+ e
    }

    val graph: RunnableGraph[NotUsed] = startSource[E](0).to(sink)

    1 to expected foreach { i =>
      actors.values.toSeq(Random.nextInt(actors.size)) ! alterCommand(i)
    }

    graph.run()

    println(s"query all events, expecting $expected events")
    eventually {
      println(events.size)
      if (events.size >= expected - 5) checkConsecutive(events)
      events.size shouldBe expected
    }

  }

  test("query persistenceId events") {
    var events = List[E]()
    val sink = Sink.foreach[E] { e =>
      events = events :+ e
    }

    var expectedForPersistenceId      = 0
    val index                         = Random.nextInt(actors.size)
    val persistenceId                 = actors.keys.toSeq(index)
    val graph: RunnableGraph[NotUsed] = startSource[E](persistenceId, 0).to(sink)

    1 to expected foreach { i =>
      val chosen = Random.nextInt(actors.size)
      if (chosen == index) expectedForPersistenceId += 1
      actors.values.toSeq(chosen) ! alterCommand(i)
    }

    graph.run()

    println(s"query persistenceId events, expecting $expectedForPersistenceId events")
    eventually {
      println(events.size)
      events should have size expectedForPersistenceId
    }
    database.run(countEvents(persistenceId)).futureValue shouldEqual expectedForPersistenceId

  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    actors = (1 to numActors map { i: Int =>
      val pid = s"TestActor-$i"
      pid -> createActor(pid)
    }).toMap
  }

  type E = TestActor.Event

  def alterCommand(i: Int) = TestActor.Alter(i.toString)

  def createActor(pid: String): ActorRef = system.actorOf(TestActor.props(testProbe.ref, Some(pid)))

  def checkConsecutive(events: List[E]): Unit =
    events
      .collect { case TestActor.Altered(id, _) => id.toInt }
      .sorted
      .sliding(2)
      .find(l => if (l.size == 1) false else l.head + 1 != l(1))
      .foreach(println)

}
