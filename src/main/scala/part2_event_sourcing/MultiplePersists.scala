package part2_event_sourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

import java.util.Date

object MultiplePersists extends App {
  /*
    Diligent accountant: with every invoice, will persist TWO events:
    - a tax record for the fiscal authority
    - an invoice record for personal logs or some auditing authority

   */

  // COMMAND
  case class Invoice(recipient: String, date: Date, amount: Int)

  // EVENTS
  case class TaxRecord(taxId: String, recordId: Int, date: Date, totalAmount: Int)
  case class InvoiceRecord(invoiceRecordId: Int, recipient: String, date: Date, amount: Int)

  object DiligentAccountant {
    def props(taxId: String, taxAuthority: ActorRef) = Props(new DiligentAccountant(taxId, taxAuthority))
  }

  class DiligentAccountant(taxId: String, taxAuhority: ActorRef) extends PersistentActor
    with ActorLogging {

    var latestTaxRecordId = 0
    var latestInvoicedRecordId = 0

    override def persistenceId: String = "diligent-accountant"

    override def receiveCommand: Receive = {
      case Invoice(recipient, date, amount) =>
        // assumption of the current example: 1/3 of every single income,
        // we are going to send to the Government
        persist(TaxRecord(taxId, latestTaxRecordId, date, amount / 3)) { record =>
          taxAuthority ! record
          latestTaxRecordId += 1

          // nested persist
          persist("I hereby declare this tax record to be true and complete.") { declaration =>
            taxAuthority ! declaration
          }
        }
        persist(InvoiceRecord(latestInvoicedRecordId, recipient, date, amount)) { invoiceRecord =>
          taxAuthority ! invoiceRecord
          latestInvoicedRecordId +=1

          // nested persist
          persist("I hereby declare this tax record to be true.") { declaration =>
            taxAuthority ! declaration
          }
        }
    }

    override def receiveRecover: Receive = {
      // receiveRecover is not the main goal of this lecture
      // for that reason, we only log a message.
      case event => log.info(s"Recovered: $event")
    }
  }

  class TaxAuthority extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"Received: $message")
    }
  }

  val system = ActorSystem("MultiplePersistDemo")
  val taxAuthority = system.actorOf(Props[TaxAuthority], "Fiscal_Government")
  val accountant = system.actorOf(DiligentAccountant.props("UK1234_6789", taxAuthority))

  accountant ! Invoice("The Boring Company", new Date, 2000)

  /*
    The message ordering (`TaxRecord` and then `InvoiceRecord`) is GUARANTEED.
   */
  /**
    * PERSISTENCE IS ALSO BASED ON MESSAGE PASSING: That is Journals are actually
    * implemented using actors.
    */

  // nested persisting

  // the accountant ALWAYS first logs the message for the `The Boring Company` and then, it logs
  // the `The Supercar`. This is also guaranteed on a general scale because the second invoice
  // `The Supercar` is stashed until the persisting is done from the first invoice.
  accountant ! Invoice("The Supercar", new Date, 20004302)
}
