package org.bustos.tides

import akka.actor.{ Actor, ActorRef, Props, ActorSystem }
import akka.actor.ActorLogging
import scala.concurrent.duration._
import java.net.InetSocketAddress
import akka.util.ByteString
import org.slf4j.{ Logger, LoggerFactory }

object TidesController {
  case class NodeConnect(remote: InetSocketAddress)
}

class TidesController extends Actor with ActorLogging {

  import TidesController._
  import TidesConsole._
  import Node._
  import context._

  val logger = LoggerFactory.getLogger(getClass)

  var nodes = Map.empty[String, ActorRef]

  val consoleInput = actorOf(Props[TidesConsole], "tidesConsole")

  def receive = {
    case NodeConnect(address) => {
      nodes = nodes + (address.getHostString -> context.actorOf(Props(new Node(address, self)), address.getHostString))
      context.system.scheduler.schedule(Duration.Zero, 50 seconds, nodes(address.getHostString), ByteString("Tick"))
    }
    case received: ByteString => {
      println("Manager: " + received)
    }
    // Console activity
    case ConsoleInput(inputString) => {
      logger.debug(inputString)
      nodes.foreach({ case (k, v) => v ! ByteString(inputString) })
    }
    case _ => {
      logger.debug("Received Unknown message")
    }

  }

}
