package org.bustos.tides

import akka.actor.{ Actor, ActorRef, Props }
import akka.actor.Stash
import akka.actor.ActorLogging
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import org.bustos.tides.Node.{NodeWriteFailed, NodeConnectionClosed, NodeConnectionFailed}
import org.slf4j.LoggerFactory

object Node {
  def props(remote: InetSocketAddress, listener: ActorRef) = Props(classOf[Node], remote, listener)

  case class NodeConnectionFailed()
  case class NodeConnectionClosed()
  case class NodeWriteFailed()

  val UTF_8 = "UTF_8"
}

class Node(remote: InetSocketAddress, listener: ActorRef) extends Actor with ActorLogging {

  import Tcp._
  import Node._
  import context.system

  val logger = LoggerFactory.getLogger(getClass)
  var connectionTestCount = 0
  var connectionTestStart = System.currentTimeMillis
  val CONNECTION_TEST_LIMIT = 10

  IO(Tcp) ! Connect(remote)

  def receive = {
    case CommandFailed(_: Connect) =>
      logger.info("Failed connect: " + self.path.name)
      listener ! NodeConnectionFailed
      context stop self
    case c @ Connected(remote, local) =>
      logger.info("Connected: " + self.path.name)
      listener ! c
      val connection = sender()
      connection ! Register(self)
      connection ! Write(ByteString("X"))
      context become connected(connection)
    //context become connectionTest(connection)
    //connection ! Write(ByteString("P"))
  }

  def connectionTest(connection: ActorRef): Receive = {
    case Received(data) =>
      logger.info("Received: " + data.decodeString(UTF_8))
      connectionTestCount = connectionTestCount + 1
      if (connectionTestCount > CONNECTION_TEST_LIMIT) {
        logger.info("Connection test time: " + (System.currentTimeMillis - connectionTestStart))
        context become connected(connection)
      } else connection ! Write(ByteString("P"))
  }

  def connected(connection: ActorRef): Receive = {
    case data: ByteString =>
      logger.info("Send: " + data.decodeString(UTF_8))
      connection ! Write(data)
    case CommandFailed(w: Write) =>
      logger.info("Write Failed")
      // O/S buffer was full
      listener ! NodeWriteFailed
    case Received(data) =>
      logger.info(self.path.name + ": " + data.decodeString(UTF_8))
      listener ! data
    case "close" =>
      connection ! Close
    case _: ConnectionClosed =>
      listener ! NodeConnectionClosed
      context stop self
  }
}
