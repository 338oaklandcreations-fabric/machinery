package org.bustos.tides

import java.net.InetSocketAddress

import akka.actor._
import akka.util.ByteString
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object TidesController {
  case class NodeConnect(remote: InetSocketAddress)

  val NodePingFrequency = 50 seconds
  val PingMessage = ByteString("P")
}

class TidesController extends Actor with ActorLogging {

  import Node._
  import TidesConsole._
  import TidesController._
  import context._

  val logger = LoggerFactory.getLogger(getClass)

  var nodes = Map.empty[String, ActorRef]
  var nodePings = Map.empty[String, Cancellable]

  val consoleInput = actorOf(Props[TidesConsole], "tidesConsole")

  def addNode(node: ActorRef) = {
    nodes = nodes + (node.path.name -> node)
    nodePings = nodePings + (node.path.name -> system.scheduler.schedule(NodePingFrequency, NodePingFrequency, nodes(node.path.name), PingMessage))
  }

  def dropNode(node: ActorRef) = {
    logger.info("Node dropped: " + node.path.name)
    nodes = nodes - node.path.name
    nodePings = nodePings - node.path.name
  }

  def receive = {
    case NodeConnect(address) => {
      addNode(context.actorOf(Props(new Node(address, self)), address.getHostString))
    }
    case NodeConnectionClosed => {
      dropNode(sender())
    }
    case NodeConnectionFailed => {
      dropNode(sender())
    }
    case NodeWriteFailed => {
      logger.info("Write Failed")
    }
    case received: ByteString => {
      logger.info("Manager: " + received.decodeString(UTF_8))
    }
    // Console activity
    case ConsoleInput(inputString) => {
      logger.debug("Console input: " + inputString)
      nodes.foreach({ case (k, v) => v ! ByteString(inputString) })
    }
    case _ => {
      logger.debug("Received Unknown message")
    }

  }

}
