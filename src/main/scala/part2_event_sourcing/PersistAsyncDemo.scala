package part2_event_sourcing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistAsyncDemo extends App {

  case class Command(contents: String)
  case class Event(contents: String)

  object CriticalStreamProcessor {
    def props(eventAggregator: ActorRef) = Props(new CriticalStreamProcessor(eventAggregator))
  }

  class CriticalStreamProcessor(eventAggregator: ActorRef) extends PersistentActor with ActorLogging {
    override def persistenceId: String = "critical-stream-processor"

    override def receiveCommand: Receive = {
      case Command(contents) =>
        eventAggregator ! s"Processing $contents"
        persistAsync(Event(contents)) /*                 TIME GAP                        */ { e =>
          eventAggregator ! e
        }

        // Assume CriticalStreamProcessor Actor in practice does some actual computation
        // and finally it's going to persist its processed event.
        val processedContents = contents + "_processed"
        persistAsync(Event(processedContents)) /*                 TIME GAP                        */ { e =>
          eventAggregator ! e
        }
    }

    override def receiveRecover: Receive = {
      case message => log.info(s"Recovered: $message")
    }
  }

  class EventAggregator extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(s"$message")
    }
  }

  val system = ActorSystem("PersistAsyncDemo")
  val eventAggregator = system.actorOf(Props[EventAggregator], "eventAggregator")
  val streamProcessor = system.actorOf(CriticalStreamProcessor.props(eventAggregator), "StreamProcessor")

  streamProcessor ! Command("command 1")
  streamProcessor ! Command("command 2")

  /*
    The difference between `persist` and `persistAsync` is the time gap between the call
    and the handler (see TIME GAP comment in the code) .

    With the `persist` method STASHES every single incoming message
    With the `persistAsync` method DOESN'T STASH messages in the TIME GAP

    So, the time the Actor actually spends executing code is small compared to the TIME GAP
    in between the persistAsync calls and the actual callback handler.

    Note: both `persist` and `persistAsync` are asynchronous methods, but `persistAsync` means
    that you can also receive normal commands in the meantime, so, the `persistAsync` call plus the
    handler as a combo is not atomic with respect to incoming messages anymore. So `persistAsync`
    relaxes the persist guarantees.

    However, the common ground between `persist` and `persistAsync` is that they're both based on
    sending messages.
   */


  /**

    persistAsync vs persist

    persistAsync is good.
    - performance: high-throughput environment

    persistAsync is bad.
    - when you absolutely need the ordering of events: for example, when the state depends on it.
      (no offer ordering guarantees)
    */
}
