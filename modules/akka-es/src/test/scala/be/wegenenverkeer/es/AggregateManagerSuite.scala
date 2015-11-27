package be.wegenenverkeer.es

import akka.actor._
import akka.testkit.{TestProbe, TestKit}
import be.wegenenverkeer.es.actor.{PassivationConfig, GracefulPassivation}
import be.wegenenverkeer.es.domain.AggregateRoot.Command
import be.wegenenverkeer.es.service.AggregateManager
import org.scalatest.{ShouldMatchers, BeforeAndAfterEach, FunSuiteLike}

//messages intended for testing AggregateManager to check its state
case object GetAggregateCreatedCount
case class GetBufferedCount(ref: ActorRef)
case class IsPassivating(actorRef: ActorRef)
case class IsChild(actorRef: ActorRef)
case class hasChild(id: String)

//only real command
case object FooCommand extends Command {
  override val metadata = Map.empty[String, String]
}
//custom death message, used while Passivating
case object CustomPoisonPill

class FooAggregateManager extends AggregateManager {

  override val maxBufferSize: Int = 2
  var aggregateCreatedCount: Int = 0

  override def processCommand = {
    case (id: String, FooCommand) => processAggregateCommand(id, FooCommand)
    case GetAggregateCreatedCount => sender ! aggregateCreatedCount
    case IsPassivating(ref)       => sender ! passivatingBuffers.contains(ref)
    case IsChild(ref)             => sender ! isChild(ref)
    case hasChild(id)             => sender ! context.child(id).isDefined
    case GetBufferedCount(ref)    => sender ! passivatingBuffers.get(ref).map(_.size).getOrElse(0)
  }

  override def aggregateProps(id: String): Props = Props(new FooAggregate())

  override protected def create(id: String): ActorRef = {
    aggregateCreatedCount += 1
    super.create(id)
  }
}


class FooAggregate extends Actor with GracefulPassivation {

  override val pc: PassivationConfig = PassivationConfig(CustomPoisonPill)

  override def receive: Receive = {
    case FooCommand => sender ! self
    case CustomPoisonPill =>
  }
}

class AggregateManagerSuite extends TestKit(ActorSystem())
  with FunSuiteLike
  with ShouldMatchers
  with BeforeAndAfterEach {

  var testProbe: TestProbe = _
  var aggregateManager: ActorRef = _

  override protected def beforeEach() = {
    testProbe = TestProbe()
    aggregateManager = system.actorOf(Props(new FooAggregateManager()))
    testProbe.send(aggregateManager, GetAggregateCreatedCount)
    testProbe.expectMsg(0)
    ()
  }

  test("sending invalid command does not create an aggregate child") {
    testProbe.send(aggregateManager, "foo")
    testProbe.expectNoMsg()
    testProbe.send(aggregateManager, GetAggregateCreatedCount)
    testProbe.expectMsg(0)
    ()
  }

  test("sending valid command does create an aggregate child") {
    testProbe.send(aggregateManager, ("myId", FooCommand))
    val fooActor = testProbe.expectMsgClass(classOf[ActorRef])

    //aggregate manager will have created a new aggregate
    testProbe.send(aggregateManager, GetAggregateCreatedCount)
    testProbe.expectMsg(1)

    //send message again with same aggregate id
    testProbe.send(aggregateManager, ("myId", FooCommand))
    testProbe.expectMsgClass(classOf[ActorRef]) shouldBe fooActor
    //no extra aggregate should have been created
    testProbe.send(aggregateManager, GetAggregateCreatedCount)
    testProbe.expectMsg(1)
    ()
  }

  test("sending valid commands for different aggregates") {
    testProbe.send(aggregateManager, ("myId1", FooCommand))
    val fooActor = testProbe.expectMsgClass(classOf[ActorRef])

    testProbe.send(aggregateManager, ("myId2", FooCommand))
    testProbe.expectMsgClass(classOf[ActorRef]) shouldNot be(fooActor)

    //aggregate manager will have create two aggregates
    testProbe.send(aggregateManager, GetAggregateCreatedCount)
    testProbe.expectMsg(2)
    ()
  }

  test("passivating aggregate should work") {
    testProbe.send(aggregateManager, ("myId", FooCommand))
    val fooActor = testProbe.expectMsgClass(classOf[ActorRef])

    testProbe.send(aggregateManager, IsPassivating(fooActor))
    testProbe.expectMsg(false)
    testProbe.send(aggregateManager, GetBufferedCount(fooActor))
    testProbe.expectMsg(0)

    //sending ReceiveTimeout will cause FooActor to send a Passivate message to AggregateManager
    testProbe.send(fooActor, ReceiveTimeout)
    testProbe.expectNoMsg()
    testProbe.send(aggregateManager, IsPassivating(fooActor))
    testProbe.expectMsg(true)

    //now kill the actor
    testProbe.watch(fooActor)
    testProbe.send(fooActor, PoisonPill)
    testProbe.expectTerminated(fooActor)

    //actor is no longer a child of aggregateManager
    testProbe.send(aggregateManager, IsChild(fooActor))
    testProbe.expectMsg(false)
    testProbe.send(aggregateManager, hasChild("myId"))
    testProbe.expectMsg(false)

    testProbe.send(aggregateManager, GetAggregateCreatedCount)
    testProbe.expectMsg(1)
    ()
  }

  test("sending command to passivating aggregate should be buffered") {
    testProbe.send(aggregateManager, ("myId", FooCommand))
    val fooActor = testProbe.expectMsgClass(classOf[ActorRef])

    //sending ReceiveTimeout will cause FooActor to send a Passivate message to AggregateManager
    testProbe.send(fooActor, ReceiveTimeout)
    testProbe.expectNoMsg()
    testProbe.send(aggregateManager, IsPassivating(fooActor))
    testProbe.expectMsg(true)
    testProbe.send(aggregateManager, GetBufferedCount(fooActor))
    testProbe.expectMsg(0)

    //sending command to passivating actor will be buffered
    testProbe.send(aggregateManager, ("myId", FooCommand))
    testProbe.expectNoMsg()
    testProbe.send(aggregateManager, GetBufferedCount(fooActor))
    testProbe.expectMsg(1)

    //sending another command to passivating actor will also be buffered
    testProbe.send(aggregateManager, ("myId", FooCommand))
    testProbe.expectNoMsg()
    testProbe.send(aggregateManager, GetBufferedCount(fooActor))
    testProbe.expectMsg(2)

    //sending yet another command to passivating actor will not be buffered anymore
    testProbe.send(aggregateManager, ("myId", FooCommand))
    testProbe.expectNoMsg()
    testProbe.send(aggregateManager, GetBufferedCount(fooActor))
    testProbe.expectMsg(2)

    testProbe.send(aggregateManager, GetAggregateCreatedCount)
    testProbe.expectMsg(1)

    //now kill the actor
    testProbe.watch(fooActor)
    testProbe.send(fooActor, PoisonPill)
    testProbe.expectTerminated(fooActor)

    //expect 2 answers to buffered commands
    val newFooActor = testProbe.expectMsgClass(classOf[ActorRef])
    testProbe.expectMsgClass(classOf[ActorRef]) shouldBe newFooActor

    //fooActor is recreated because there were outstanding messages
    testProbe.send(aggregateManager, IsChild(fooActor))
    testProbe.expectMsg(false)

    testProbe.send(aggregateManager, hasChild("myId"))
    testProbe.expectMsg(true)

    testProbe.send(aggregateManager, GetAggregateCreatedCount)
    testProbe.expectMsg(2)

    testProbe.send(aggregateManager, IsPassivating(newFooActor))
    testProbe.expectMsg(false)

    testProbe.send(aggregateManager, GetBufferedCount(fooActor))
    testProbe.expectMsg(0)
    ()
  }
}
