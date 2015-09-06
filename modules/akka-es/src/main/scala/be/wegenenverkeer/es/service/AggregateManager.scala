package be.wegenenverkeer.es.service

import akka.actor._
import be.wegenenverkeer.es.actor.Passivate
import be.wegenenverkeer.es.domain.AggregateRoot.Command

/**
  * Base aggregate manager.
  * Handles communication between client and aggregate.
  * It is also responsible for aggregates creation and removal and passivation handling
  */
trait AggregateManager extends Actor with ActorLogging {

  def maxBufferSize = 1000

  var passivatingBuffers = Map.empty[ActorRef, Vector[(Command, ActorRef)]]

  def totalBufferSize = {
    passivatingBuffers.map { case (_, buf) => buf.size }.sum
  }


  /**
    * Processes command.
    * In most cases it should transform message to appropriate aggregate command (and apply some additional logic if needed)
    * and call [[AggregateManager.processAggregateCommand]]
    */
   def processCommand: Receive

   override def receive: Receive = processCommand orElse {
     case Passivate(stopMessage) =>
       passivate(sender(), stopMessage)
     case Terminated(actorRef) =>
       log.debug("actor was terminated {}", actorRef)
       if (passivatingBuffers.contains(actorRef)) {
         val buffers = passivatingBuffers(actorRef)
         log.debug("Passivating completed {}, buffered [{}]", actorRef, buffers.size)
         // deliver messages that were received between Passivate and Terminated,
         // will create new entry instance and deliver the messages to it
         passivatingBuffers -= actorRef
         buffers foreach {
           case (command, snd) => processAggregateCommand(actorRef.path.name, command, snd)
         }
       }
   }

   /**
    * Processes aggregate command.
    * Creates an aggregate (if not already created) and handles commands caching while aggregate is passivating
    *
    * @param aggregateId Aggregate id
    * @param command Command that should be passed to aggregate
    */
   def processAggregateCommand(aggregateId: String, command: Command, snd: ActorRef = sender()): Unit = {
     val aggregate = findOrCreate(aggregateId)
     passivatingBuffers.get(aggregate) match {
       case None =>
         log.debug("forwarding to aggregate {}", aggregate)
         aggregate.tell(command, snd)
       case Some(buf) =>
         if (totalBufferSize >= maxBufferSize) {
           log.debug("Buffer is full, dropping message for passivated aggregate {}", aggregate)
           context.system.deadLetters ! command
         } else {
           log.debug("Command for aggregate {} buffered due to entry being passivated", aggregate)
           passivatingBuffers = passivatingBuffers.updated(aggregate, buf :+ ((command, snd)))
         }
     }
   }

   protected def findOrCreate(id: String): ActorRef =
     context.child(id) getOrElse {
       log.debug("creating child aggregate {}", id)
       create(id)
     }

   protected def create(id: String): ActorRef = {
     val aggregate = context.actorOf(aggregateProps(id), id)
     context watch aggregate
     aggregate
   }

  def passivate(entry: ActorRef, stopMessage: Any): Unit = {
    if (!passivatingBuffers.contains(entry) && isChild(entry)) {
      passivatingBuffers = passivatingBuffers.updated(entry, Vector.empty)
      entry ! stopMessage
    }
  }
  
  def isChild(child: ActorRef): Boolean = {
    context.child(child.path.name) match {
      case None => false
      case Some(ref) => ref == child
    }
  }

   /**
    * Returns Props used to create an aggregate with specified id
    *
    * @param id Aggregate id
    * @return Props to create aggregate
    */
   def aggregateProps(id: String): Props

 }
