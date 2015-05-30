package com._338oaklandcreations.fabric.machinery

import java.net.InetSocketAddress

import akka.actor._
import akka.util.ByteString
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object MachineryController {
  case class NodeConnect(remote: InetSocketAddress, mac: String)

  val NodePingFrequency = 50 seconds
  val PingMessage = ByteString("P")
}

class MachineryController extends Actor with ActorLogging {

  import Node._
  import MachineryConsole._
  import MachineryController._
  import context._

  val logger = LoggerFactory.getLogger(getClass)

  var nodes = Map.empty[String, ActorRef]
  var nodePings = Map.empty[String, Cancellable]

  logger.info("Starting Controller")

  val consoleInput = actorOf(Props[MachineryConsole], "machineryConsole")
  val fadecandy = actorOf(Props[OpcActor], "opcController")

  def reportCountStatus = {
    logger.info("Count of connected nodes: " + nodes.size)
  }

  def addNode(node: ActorRef) = {
    nodes = nodes + (node.path.name -> node)
    nodePings = nodePings + (node.path.name -> system.scheduler.schedule(NodePingFrequency, NodePingFrequency, nodes(node.path.name), PingMessage))
    reportCountStatus
  }

  def dropNode(node: ActorRef) = {
    logger.warn("Node dropped: " + node.path.name)
    nodes = nodes - node.path.name
    nodePings = nodePings - node.path.name
    reportCountStatus
  }

  def receive = {
    case NodeConnect(address, mac) => {
      addNode(context.actorOf(Props(new Node(address, self)), mac + "@" + address.getHostString + ":" + address.getPort))
    }
    case NodeConnectionClosed => {
      dropNode(sender())
    }
    case NodeConnectionFailed => {
      dropNode(sender())
    }
    case NodeWriteFailed => {
      logger.warn("Write Failed")
    }
    case received: ByteString => {
      logger.info("Received: " + received.decodeString(UTF_8))
    }
    // Console activity
    case ConsoleInput(inputString) => {
      logger.info("Console input: " + inputString)
      nodes.foreach({ case (k, v) => v ! ByteString(inputString) })
    }
    case _ => {
      logger.debug("Received Unknown message")
    }

  }

}
