package part3_stores_serialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object LocalStore extends App {

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
