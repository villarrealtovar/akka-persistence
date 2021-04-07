package part4_practices

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor
import akka.persistence.journal.{EventSeq, ReadEventAdapter}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable

object EventAdapters extends App {

  /**
    * For the purpose of this lecture, assume that we're
    * designing an online store for acoustic guitars.
    *
    * And we have a little actor that will manage the inventory
    * of our acoustic guitar
    */
    val ACOUSTIC = "acoustic"
    val ELECTRIC = "electric"

    // data structures
    case class Guitar(id: String, model: String, make: String, guitarType: String = ACOUSTIC)

    // command
    case class AddGuitar(guitar: Guitar, quantity: Int)

    // event
    case class GuitarAdded(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int)
    case class GuitarAddedV2(guitarId: String, guitarModel: String, guitarMake: String, quantity: Int, guitarType: String)
    class InventoryManager extends PersistentActor with ActorLogging {
      val inventory: mutable.Map[Guitar, Int] = new mutable.HashMap[Guitar, Int]()
      override def persistenceId: String = "guitar-inventory-manager"

      override def receiveCommand: Receive = {
        case AddGuitar(guitar @ Guitar(id, model, make, guitarType), quantity) =>
          persistAsync(GuitarAddedV2(id, model, make, quantity, guitarType)) { _ =>
            addGuitarInventory(guitar, quantity)
            log.info(s"Added $quantity x $guitar to inventory")
          }
        case "print" =>
          log.info(s"Current inventory is: $inventory")
      }

      override def receiveRecover: Receive = {
        case event @ GuitarAddedV2(id, model, make, quantity, guitarType) =>
          log.info(s"Recovered $event")
          val guitar = Guitar(id, model, make, guitarType)
          addGuitarInventory(guitar, quantity)
      }

      def addGuitarInventory(guitar: Guitar, quantity: Int): Unit = {
        val existingQuantity = inventory.getOrElse(guitar, 0)
        inventory.put(guitar, existingQuantity + quantity)
      }

    }

    // ReadEventAdapter was built specifically for schema evolution problem.
    class GuitarReadEventAdapter extends ReadEventAdapter {
      /*
        journal (sends bytes) ->
        serializer (deserializes bytes to GuitarAdded Event) ->
        read event adapter (Transform GuitarAdded Event to GuitarAddedV2 event) ->
        actor (handle events in the receiveRecover)

       */

      /**
        * `fromJournal` method takes an `event` in the form of an object which
        * is the output of deserialization step and a `manifest` as string which
        * we can use to instantiate some other type.
        * the output of this method is a `EventSeq` that is a sequence of multiple
        * events that the actor will handle in turn: if you somehow decide to split
        * the event in multiple events, this `EventSeq` type will handle that case
        * */
      override def fromJournal(event: Any, manifest: String): EventSeq = event match {
        case GuitarAdded(guitarId, guitarModel, guitarMake, quantity) =>
          EventSeq.single(GuitarAddedV2(guitarId, guitarModel, guitarMake, quantity, ACOUSTIC))
        case other =>
          EventSeq.single(other)

      }
    }



    val system = ActorSystem("eventAdapters", ConfigFactory.load().getConfig("eventAdapters"))
    val inventoryManager = system.actorOf(Props[InventoryManager], "inventoryManager")

/*    val guitars = for (i <- 1 to 10) yield Guitar(s"$i", s"Hakker $i", "JAVT")
    guitars.foreach { guitar =>
      inventoryManager ! AddGuitar(guitar, 5)
    }*/

    inventoryManager ! "print"

  /*
     Also exist `WriteEventAdapter`: opposite conversion from ReadEventAdapter

      actor -< write event adapter -> serializer -> journal

     The `WriteEventAdapter` has the `toJournal` (instead of `fromJournal`).
     `WriteEventAdapter` is usually used for backwards compatibility

     If you decide to write the same logic in the `ReadEventAdapter` and `WriteEventAdapter`
     instead of duplicating the code, you'll just simply extend from `EventAdapter` which
     is simply a trait that mixes in both `ReadEventAdapter` and `WriteEventAdapter`

   */

}
