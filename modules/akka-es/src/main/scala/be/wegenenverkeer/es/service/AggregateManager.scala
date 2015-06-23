package be.wegenenverkeer.es.service

import akka.actor._
import be.wegenenverkeer.es.domain.AggregateRoot.Command

/**
  * Base aggregate manager.
  * Handles communication between client and aggregate.
  * It is also capable of aggregates creation and removal.
  */
trait AggregateManager extends Actor with ActorLogging {

   /**
    * Processes command.
    * In most cases it should transform message to appropriate aggregate command (and apply some additional logic if needed)
    * and call [[AggregateManager.processAggregateCommand]]
    */
   def processCommand: Receive

   override def receive: Receive = processCommand orElse {
     case Terminated(actorRef) =>
       log.error(s"actor was terminated $actorRef")
       //TODO what should we do now => nothing because ask command will timeout ?
   }

   /**
    * Processes aggregate command.
    * Creates an aggregate (if not already created) and handles commands caching while aggregate is being killed.
    *
    * @param aggregateId Aggregate id
    * @param command Command that should be passed to aggregate
    */
   def processAggregateCommand(aggregateId: String, command: Command): Unit = {
     val child = findOrCreate(aggregateId)
     log.debug(s"forwarding to child $child")
     child forward command
   }

   protected def findOrCreate(id: String): ActorRef =
     context.child(id) getOrElse {
       log.debug(s"creating $id")
       create(id)
     }

   protected def create(id: String): ActorRef = {
     val agg = context.actorOf(aggregateProps(id), id)
     context watch agg
     agg
   }

   /**
    * Returns Props used to create an aggregate with specified id
    *
    * @param id Aggregate id
    * @return Props to create aggregate
    */
   def aggregateProps(id: String): Props

 }
