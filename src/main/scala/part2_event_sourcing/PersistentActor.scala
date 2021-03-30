package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

import java.util.Date

object PersistentActor extends App {

  /*
    Scenario: We have a business and an accountant which keeps track of our invoices.
   */

  // This will be the COMMAND for this accountant
  case class Invoice(recipient: String, date: Date, amount: Int)

  // EVENTS
  case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int)

  class Accountant extends PersistentActor with ActorLogging {
    var latestInvoiceId = 0
    var totalAmount = 0

    /**
      * `persistenceId` is how the events persisted by this actor
      * will be identified in a persistent store.
      *
      * This persistenceId should be unique per each actor because we have
      * lots and lots of events grouped into one place so we'll have
      * to know which actor wrote what event.
      * */
    override def persistenceId: String = "simple-accountant" // best practice: make it unique

    /**
      * The "normal" `receive` method.
      * */
    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        /*
            When you receive a command
            1) you create an EVENT to persist into the store
            2) you persist the event, then pass in a callback that will be
               triggered once the event is written
            3) update the actor's state when the event has persisted
         */
        log.info(s"Receive invoice for amount: $amount")
        val event = InvoiceRecorded(latestInvoiceId, recipient, date, amount) // step 1)

        /*
          the listener and watcher may notice a time gap between the original call to persist
          `persist` and the callback which is triggered after the event has been persist.
          Here we have a time gap between

          Now, what if other messages are handled in the meantime between persisting and the callback?
          What if other commands are being sent in the meantime.
          `Akka persist` guarantees that all the messages between persisting and the event handling
          callback of the persisted event are stashed: So, all other message to this actor
          are STASHED
        */
        persist(event) { e =>

          // SAFE to access mutable state here: NO race conditions and doesn't break
          // encapsulation even we are updating mutable state and/or calling methods
          // inside of a asynchronous method:
          // `persist` is asynchronous and no blocking, and it has a callback,
          // and this will be executed in the future, but this is OK because is SAFE because
          // `Akka Persistence` guarantees that no other threads are accessing the actor during
          // a callback.

          // update mutable state
          latestInvoiceId += 1
          totalAmount += amount

          // we can even safely access the right sender of the command during the process callback
          // correctly identify the sender of the command.
          sender() ! "Persistence ACK"
          log.info(s"Persisted $e as invoice #${e.id}, for total amount $totalAmount")
        }
        // act like a normal actor
      case "print" => log.info(s"Lates invoice id: $latestInvoiceId, total amount: $totalAmount")
    }

    /**
      * Handler that will be called on recovery
      * */
    override def receiveRecover: Receive = {
      /*
        best practice: follow the logic in the persist steps of receiveCommand
       */
      case InvoiceRecorded(id, _, _, amount) =>
        latestInvoiceId = id
        totalAmount += amount
        log.info(s"Recovered invoice #$id for amount $amount, total amount: $totalAmount")
    }
  }

  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant], "simpleAccountant")

  /* for(i <- 1 to 10) {
    accountant ! Invoice("The Boring Company", new Date, i * 1000)
  } */
}
