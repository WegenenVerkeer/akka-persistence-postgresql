package gatlin.simulations

import com.typesafe.config.ConfigFactory
import gatlin.{MultiActorPerfSimulation, SingleActorPerfSimulation}

class TransactionalSingleActorSimulation extends SingleActorPerfSimulation(ConfigFactory.load("pg-perf-tx.conf"))
class TransactionalMultiActorSimulation extends MultiActorPerfSimulation(ConfigFactory.load("pg-perf-tx.conf"))

class RowIdUpdatingSingleActorSimulation extends SingleActorPerfSimulation(ConfigFactory.load("pg-perf-rowid.conf"))
class RowIdUpdatingMultiActorSimulation extends MultiActorPerfSimulation(ConfigFactory.load("pg-perf-rowid.conf"))

class SingleThreadedSingleActorSimulation extends SingleActorPerfSimulation(ConfigFactory.load("pg-perf-st.conf"))
class SingleThreadedMultiActorSimulation extends MultiActorPerfSimulation(ConfigFactory.load("pg-perf-st.conf"))

class TableLockingSingleActorSimulation extends SingleActorPerfSimulation(ConfigFactory.load("pg-perf-locking.conf"))
class TableLockingMultiActorSimulation extends MultiActorPerfSimulation(ConfigFactory.load("pg-perf-locking.conf"))
