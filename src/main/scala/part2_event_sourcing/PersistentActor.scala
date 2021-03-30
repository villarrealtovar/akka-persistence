package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, PoisonPill, Props}
import akka.persistence.PersistentActor

import java.util.Date

object PersistentActor extends App {

  /*
    Scenario: We have a business and an accountant which keeps track of our invoices.
   */

  // This will be the COMMAND for this accountant
  case class Invoice(recipient: String, date: Date, amount: Int)
  case class InvoiceBulk(invoices: List[Invoice])
  case object ShutDown

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
      case InvoiceBulk(invoices) =>
        /*
          Follow the predefined pattern:

          1) create events (plural)
          2) persist all the events
          3) update the actor state when each event is persisted
         */
        val invoiceIds = latestInvoiceId to (latestInvoiceId + invoices.size)
        val events = invoices.zip(invoiceIds).map { pair =>
          val invoice = pair._1
          val id = pair._2

          InvoiceRecorded(id, invoice.recipient, invoice.date, invoice.amount)
        }

        // Note: the `persistAll` callback is executed after each event has been successfully
        // written to the journal.
        persistAll(events) { e => // `e` is of type InvoiceRecord
          latestInvoiceId += 1
          totalAmount += e.amount
          log.info(s"Persisted SINGLE $e as invoice #${e.id}, for total amount $totalAmount")
        }
      case ShutDown =>
        context.stop(self)
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

    /*
    * This method is called if persisting failed.
    * The actor will be STOPPED regardless of the supervision strategy: the reason is that
    * since you don't know if the event was persisted or not the actor is in an inconsistent
    * state. So it cannot be trusted even if it's resumed. When this happens it's a good idea
    * to start the actor again after a while. In the backoff Supervisor Pattern is useful here.
    *
    * Best Practice: start the actor again after a while (use Backoff Supervisor Pattern)
    *
    * This particular scenario is extremely hard to reproduce because persisting almost never
    * throws, but it's good to know if persisting fails what to do.
    * */
    override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Fail to persist $event because of $cause")
      super.onPersistFailure(cause, event, seqNr)
    }

    /*
     * Called if the JOURNAL throws an exception while persisting the event (fails to persist the event)
     *
     * The actor is RESUMED because the actor's state wasn't corrupted in the process.
     *
     * This is scenario is very hard to test because the Journals that we use are pretty robust
     * and almost never thrown exception.
     * */
    override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Fail to persist $event because of $cause")
      super.onPersistRejected(cause, event, seqNr)
    }
  }

  val system = ActorSystem("PersistentActors")
  val accountant = system.actorOf(Props[Accountant], "simpleAccountant")

  for(i <- 1 to 10) {
    accountant ! Invoice("The Boring Company", new Date, i * 1000)
  }

  /**
    A. Persistence failures

    There are tow type of persistence failures that we want to discuss:
    1) failures to persist in the `persist` method call throws an error
    2) the Journal implementation actually fails to persist a message

    * */

  /**
    B. Persisting multiple events

    Scenario: for some reason, you want to send a bulk message, so a list of
    invoices to the Accountant Actor, so that it can persist multiple
    invoiceRecorded events at the same time.

    The solution is `persistAll`

    * */
    val newInvoices = for( i <- 1 to 5) yield Invoice("The awesome chairs", new Date, i *2000)
    // accountant ! InvoiceBulk(newInvoices.toList)

  /*
      NEVER EVER CALL PERSIST OR PERSISTALL FROM FUTURES: Otherwise you risk breaking the
      actor encapsulation because the actor thread is free to process messages while
      you're persisting and, if the normal actor thread also calls persist you suddenly have
      two threads persisting events simultaneously. So, because the event order is non-deterministic
      you risk corrupting the actor state.
   */

  /**
    C. Shutdown of persistent actors

    Best Practice: define your own shutdown message
    * */

  // accountant ! PoisonPill
  accountant ! ShutDown
}
