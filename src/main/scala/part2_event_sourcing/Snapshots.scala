package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}

import scala.collection.mutable

object Snapshots extends App {


  // Commands
  case class ReceivedMessage(contents: String) // message FROM your contact
  case class SentMessage(contents: String) // message TO your contact

  // Events
  case class ReceiveMessageRecord(id: Int, contents: String)
  case class SentMessageRecord(id: Int, contents: String)

  object Chat {
    def props(owner: String, contact: String) = Props(new Chat(owner, contact))
  }


  class Chat(owner: String, contact: String) extends PersistentActor with ActorLogging {
    /*
      Let's assume that this little chat message only can hold a limited number of
      messages in memory mainly because you have like millions and millions of users
      and due to memory constraints you can't hold many of these chat actors in memory
      at any one time.

      So, this Chat Actor can hold the last 10 messages in memory and all the other messages
      are persisted as records (ReceiveMessageRecord, SentMessageRecord)
     */
    val MAX_MESSAGES = 10

    // State
    var commandsWithoutCheckpoint = 0
    var currentMessageId = 0

    /*
      When you send a message, I'm going to put owner and content here in the queue and
      when I receive a message, I'm going to put my contact and the contents in the queue.
     */
    val lastMessages = new mutable.Queue[(String, String)]()// queue of the last ten messages that we have sent in this this chatroom

    override def persistenceId: String = s"$owner-$contact-chat"

    override def receiveCommand: Receive = {
      case ReceivedMessage(contents) =>
        persist(ReceiveMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Received message: $contents")
          maybeReplaceMessage(contact, contents)
          maybeCheckpoint()
          currentMessageId += 1
        }
      case SentMessage(contents) =>
        persist(SentMessageRecord(currentMessageId, contents)) { e =>
          log.info(s"Sent message: $contents")
          maybeReplaceMessage(owner, contents)
          currentMessageId += 1
          maybeCheckpoint()
        }
      case "print" =>
        log.info(s"Most recent messages: $lastMessages")
      // snapshot-related messages
      case SaveSnapshotSuccess(metadata) =>
        log.info(s"saving snapshot succeeded: $metadata")
      case SaveSnapshotFailure(metadata, reason) =>
        log.warning(s"Saving snapshot $metadata failed because of $reason")
    }

    override def receiveRecover: Receive = {
      case ReceiveMessageRecord(id, contents) =>
        log.info(s"Recovered received message $id: $contents")
        maybeReplaceMessage(contact, contents)
        currentMessageId = id
      case SentMessageRecord(id, contents) =>
        log.info(s"Recovered sent message $id: $contents")
        maybeReplaceMessage(owner, contents)
        currentMessageId = id
      case SnapshotOffer(metadata, contents) => // Special Message
        log.info(s"Recovered snapshot: $metadata")
        contents.asInstanceOf[mutable.Queue[(String, String)]].foreach(lastMessages.enqueue(_))
    }

    def maybeReplaceMessage(sender: String, contents: String): Unit = {
      if (lastMessages.size >= MAX_MESSAGES) {
        lastMessages.dequeue()
      }
      lastMessages.enqueue((sender, contents))
    }

    def maybeCheckpoint(): Unit = {
      commandsWithoutCheckpoint += 1
      if (commandsWithoutCheckpoint >= MAX_MESSAGES) {
        log.info("Saving Checkpoint...")
        saveSnapshot(lastMessages) // asynchronous operation
        commandsWithoutCheckpoint = 0
      }
    }
  }

  val system = ActorSystem("SnapshotsDemo")
  val chat = system.actorOf(Chat.props("Villarrealtovar", "CaroPaz84"))

/*
  for( i <- 1 to 100000) {
    chat ! ReceivedMessage(s"Akka Rocks $i")
    chat ! SentMessage(s"Akka rules $i")
  }
*/

  // Recovering all messages (100.000) takes a long time (seconds). The solution is SNAPSHOTS
  chat ! "print"

  /**
    Pattern for Snapshots:

    - After each persist, maybe save a snapshot (logic is up to you)
    - If you save a snapshot, handle the `SnapshotOffer`` message in receiveRecover
    - (optional, but this is a Best Practice) handle `SaveSnapshotSuccess` and `SaveSnapshotFailure`
      in receiveCommand
    - profit from the extra speed!!

    */
}
