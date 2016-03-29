/*

    Copyright (C) 2016 Mauricio Bustos (m@bustos.org) & 338.oakland creations

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

*/

package com._338oaklandcreations.fabric.machinery

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import org.slf4j.LoggerFactory

object LedController {
  def props(remote: InetSocketAddress, listener: ActorRef) = Props(classOf[LedController], remote, listener)

  case object NodeConnectionFailed
  case object NodeConnectionClosed
  case object NodeWriteFailed

  case class Pattern(patternId: Int)

  val UTF_8 = "UTF-8"
}

class LedController(remote: InetSocketAddress, listener: ActorRef) extends Actor with ActorLogging {

  import LedController._
  import Tcp._
  import context.system

  val logger = LoggerFactory.getLogger(getClass)
  var connectionTestCount = 0
  var connectionTestStart = 0L
  val CONNECTION_TEST_LIMIT = 10

  IO(Tcp) ! Connect(remote)

  def receive = {
    case CommandFailed(_: Connect) =>
      logger.warn("Failed connect: " + self.path.name)
      listener ! NodeConnectionFailed
      context stop self
    case c @ Connected(remote, local) =>
      logger.info("Connected: " + self.path.name)
      listener ! c
      val connection = sender()
      connection ! Register(self)
      connection ! Write(ByteString("X"))
      context become connectionTest(connection)
      connection ! Write(ByteString("P"))
  }

  def connectionTest(connection: ActorRef): Receive = {
    case Received(data) =>
      if (connectionTestCount > CONNECTION_TEST_LIMIT) {
        val testTime = ((System.currentTimeMillis - connectionTestStart) / connectionTestCount)
        logger.info("Round trip time: " + testTime + " ms")
        context become connected(connection)
      } else {
        if (connectionTestCount == 0) connectionTestStart = System.currentTimeMillis
        connectionTestCount = connectionTestCount + 1
        connection ! Write(ByteString("P"))
      }
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
      logger.info("close")
      connection ! Close
    case _: ConnectionClosed =>
      logger.info("Connection Closed")
      listener ! NodeConnectionClosed
      context stop self
    case _ => logger.info("Unknown Message")
  }
}
