package org.bustos.tides

import akka.actor.{ Actor, ActorRef, Props, ActorSystem }
import akka.actor.Stash
import akka.actor.ActorLogging
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import org.slf4j.{ Logger, LoggerFactory }

object Node {
  def props(remote: InetSocketAddress, listener: ActorRef) = Props(classOf[Node], remote, listener)
}

class Node(remote: InetSocketAddress, listener: ActorRef) extends Actor with ActorLogging {

  import Tcp._
  import context.system

  val logger = LoggerFactory.getLogger(getClass)

  IO(Tcp) ! Connect(remote)

  def receive = {
    case CommandFailed(_: Connect) =>
      logger.info("FAIL")
      listener ! "connect failed"
      context stop self
    case c @ Connected(remote, local) =>
      logger.info("CONNECT!")
      listener ! c
      val connection = sender()
      connection ! Register(self)
      connection ! Write(ByteString("X"))
      context become {
        case data: ByteString =>
          connection ! Write(data)
        case CommandFailed(w: Write) =>
          // O/S buffer was full
          listener ! "write failed"
        case Received(data) =>
          logger.info(data.toString)
          listener ! data
        case "close" =>
          connection ! Close
        case _: ConnectionClosed =>
          listener ! "connection closed"
          context stop self
      }
  }
}
