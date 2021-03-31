package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}

object RecoveryDemo extends App {

  case class Command(contents: String)
  case class Event(id: Int, contents: String)

  class RecoveryActor extends PersistentActor with ActorLogging {

    override def persistenceId: String = "recovery-actor"

    override def receiveCommand: Receive = online(0)

    def online(latestPersistedEventId: Int): Receive = {
      case Command(contents) =>
        persist(Event(latestPersistedEventId, contents)) { event =>
          log.info(s"Successfully persisted $event, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished")
          context.become(online(latestPersistedEventId + 1))
        }
    }

    override def receiveRecover: Receive = {
      case RecoveryCompleted => // special message
        // additional initialization
        log.info("I have finished recovering")
      case Event(id, contents) =>
//        if (contents.contains("314")) // Simulating an error during Recovery phase
//          throw new RuntimeException("I can't take this anymore")
        log.info(s"Recovered: $contents, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished")
        context.become(online(id+1)) // THIS will not change the handler that's going to be used during recovery
                                  // in other words `receiveRecover` method.
                                  // The `receiveRecover` method will ALWAYS, always be used during recovery
                                  // regardless of how many `context.become` methods you put inside.
                                  // AFTER recovery, the "normal" handler will be the result of ALL the stacking
                                  // of `context.become` methods.
    }

    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error("I failed recovery")
      super.onRecoveryFailure(cause, event)
    }

    // override def recovery: Recovery = Recovery(toSequenceNr = 100)
    // override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
    // override def recovery: Recovery = Recovery.none

  }

  val system = ActorSystem("RecoveryDemo")
  val recoveryActor = system.actorOf(Props[RecoveryActor], "recoveryActor")

  /*
    1. Stashing commands
   */
 /* for (i <- 1 to 1000) {
    recoveryActor ! Command(s"command $i")
  }*/
  // ALL COMMANDS SENT DURING RECOVERY ARE STASHED

  /*
    2. Failure during recovery

    - `onRecoveryFailure` + the actor is STOPPED.
  */

  /*
    3. Customizing Recovery: you can customize recovery to recover events
      up to a certain sequence of numbers doing an override to recovery method.


      override def recovery: Recovery = Recovery(toSequenceNr = 100)

      We can use this method for debugging, BUT PLEASE
      DO NOT PERSIST more event after a customized recovery method: Specially if
      the recovery method is incomplete. Note: You can do it fi you want, but only
      if you know what you're doing.

   */

  /*
    4. Recovery status or KNOWING when you're done recovering:
      - this.recoveryFinished
      - this.recoveryRunning
   */

  /*
    5. Getting a signal when you're done with Recovery
      - `RecoveryCompleted` message
   */

  /*
    6. Stateless actors
   */
  recoveryActor ! Command (s"special command 1")
  recoveryActor ! Command (s"special command 2")

}
