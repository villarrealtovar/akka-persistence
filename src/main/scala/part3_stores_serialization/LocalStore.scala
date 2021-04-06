package part3_stores_serialization

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import com.typesafe.config.ConfigFactory

object LocalStore extends App {

  class SimplePersistentActor extends PersistentActor with ActorLogging {
    // mutable state
    var nMessages = 0

    override def persistenceId: String = "Simple-persistent-actor"



    override def receiveCommand: Receive = {
      case "print" =>
        log.info(s"I have to persisted $nMessages so far")
      case "snapshot" =>
        saveSnapshot(nMessages)
      case SaveSnapshotSuccess(metadata) =>
        log.info(s"Save snapshot was successful: $metadata")
      case SaveSnapshotFailure(_, cause) =>
        log.warning(s"Save snapshot failure: $cause")
      case message => persist(message) { _ =>
        log.info(s"Persisting $message")
        nMessages += 1
      }
    }

    override def receiveRecover: Receive = {
      case RecoveryCompleted =>
        log.info("Recovery done")
      case SnapshotOffer(_, payload: Int) =>
        log.info(s"Recovered snapshot: $payload")
        nMessages = payload
      case message =>
        log.info(s"Recovered: $message")
        nMessages += 1
    }
  }

  val localStoreActorSystem = ActorSystem("localStoreSystem", ConfigFactory.load().getConfig("localStores"))
  val persistentActor = localStoreActorSystem.actorOf(Props[SimplePersistentActor], "simplePersistentActor")

  for (i <- 1 to 10) {
    persistentActor ! s"I love Akka [$i]"
  }
  persistentActor ! "print"
  persistentActor ! "snapshot"

  for (i <- 11 to 20) {
    persistentActor ! s"I love Akka [$i]"
  }

}
