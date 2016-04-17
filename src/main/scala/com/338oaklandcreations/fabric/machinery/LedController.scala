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
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object LedController {
  def props(remote: InetSocketAddress) = Props(classOf[LedController], remote)

  case object Tick
  case object NodeConnectionFailed
  case object NodeConnectionClosed
  case object NodeWriteFailed
  case object HeartbeatRequest
  case object LedControllerVersionRequest
  case class Heartbeat(timestamp: DateTime, messageType: Int, versionId: Int, frameLocation: Int, currentPattern: Int,
                       batteryVoltage: Int, frameRate: Int, memberType: Int, failedMessages: Int, patternName: String)
  case class Pattern(patternId: Int)
  case class LedControllerVersion(versionId: String, buildTime: String)

  val HeartbeatLength = 11
  val HeartbeatPatternNameLength = 11
  val HeartbeatTotalLength = HeartbeatLength + HeartbeatPatternNameLength
  val HeartbeatRequestString = "HB"
  val UTF_8 = "UTF-8"
}

class LedController(remote: InetSocketAddress) extends Actor with ActorLogging {

  import LedController._
  import Tcp._
  import context._

  val logger = LoggerFactory.getLogger(getClass)

  var lastHeartbeat = Heartbeat(new DateTime, 0, 0, 0, 0, 0, 0, 0, 0, "")

  var tickInterval = 5 seconds
  val tickScheduler = context.system.scheduler.schedule (0 milliseconds, tickInterval, self, Tick)

  def receive = {
    case CommandFailed(_: Connect) =>
      logger.warn("Failed connect to connect to LED Controller")
      context.parent ! NodeConnectionFailed
    case c @ Connected(remote, local) =>
      logger.info("Connected: " + self.path.name)
      context.parent ! c
      val connection = sender
      connection ! Register(self)
      context become connected(connection)
    case LedControllerVersionRequest =>
      context.sender ! LedControllerVersion("<Unknown>", "<Unknown")
    case Tick =>
      IO(Tcp) ! Connect(remote)
  }

  def connected(connection: ActorRef): Receive = {
    case data: ByteString =>
      logger.debug("Send: " + data.decodeString(UTF_8))
      connection ! Write(data)
    case CommandFailed(w: Write) =>
      logger.warn("Write Failed")
      // O/S buffer was full
      context.parent ! NodeWriteFailed
    case Received(data) =>
      logger.debug(self.path.name + ": " + data.toString)
      if (data.length <= HeartbeatTotalLength) {
        lastHeartbeat = Heartbeat(new DateTime, data(0).toInt, data(1).toInt, data(2).toInt * 256 + data(3).toInt, data(4).toInt,
          data(5).toInt * 256 + data(6).toInt, data(7).toInt, data(8).toInt, data(9).toInt * 256 + data(10).toInt,
          new String(data.slice(11, data.length - 1).toArray))
      }
    case Tick =>
      connection ! Write(ByteString(HeartbeatRequestString))
    case HeartbeatRequest =>
      context.sender ! lastHeartbeat
    case LedControllerVersionRequest =>
      context.sender ! LedControllerVersion("<Unknown>", "<Unknown")
    case "close" =>
      logger.info("close")
      connection ! Close
    case _: ConnectionClosed =>
      logger.info("Connection Closed")
      context.parent ! NodeConnectionClosed
      context become receive
    case _ => logger.info("Unknown Message")
  }
}
