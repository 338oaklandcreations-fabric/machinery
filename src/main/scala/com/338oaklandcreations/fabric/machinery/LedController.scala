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
import akka.util.{ByteString, Timeout}
import com._338oaklandcreations.fabric.machinery.FabricProtos.FabricWrapperMessage.Msg
import com._338oaklandcreations.fabric.machinery.FabricProtos.{PatternCommand, CommandMessage, FabricWrapperMessage}
import org.joda.time.format.{ISODateTimeFormat, DateTimeFormat}
import org.joda.time.{DateTimeZone, DateTime}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object LedController {
  def props(remote: InetSocketAddress) = Props(classOf[LedController], remote)

  case object LedControllerTick
  case object NodeConnectionFailed
  case object NodeConnectionClosed
  case object NodeWriteFailed
  case object HeartbeatRequest
  case object LedControllerVersionRequest
  case object PatternNamesRequest

  case class Heartbeat(timestamp: DateTime, messageType: Int, versionId: Int, currentPattern: Int,
                       red: Int, green: Int, blue: Int, speed: Int, intensity: Int, memberType: Int, patternName: String)
  case class Pattern(patternId: Int)
  case class LedControllerVersion(versionId: String, buildTime: String)
  case class PatternNames(names: List[String])
  case class PatternSelect(id: Int, red: Int, green: Int, blue: Int, speed: Int, intensity: Int)

  val MessageHeartbeatRequest = ByteString(FabricWrapperMessage.defaultInstance.withCommand(CommandMessage(Some(CommandMessage.CommandList.PROTOBUF_HEARTBEAT))).toByteArray)
  val MessagePatternNamesRequest = ByteString(FabricWrapperMessage.defaultInstance.withCommand(CommandMessage(Some(CommandMessage.CommandList.PROTOBUF_PATTERN_NAMES))).toByteArray)

  val OffPatternId = 6
  val FullColorPatternId = 3

  val OffCommand = PatternCommand(Some(OffPatternId), Some(0), Some(0), Some(0), Some(0), Some(0))

  val HeartbeatLength = 11
  val HeartbeatPatternNameLength = 11
  val HeartbeatTotalLength = HeartbeatLength + HeartbeatPatternNameLength
  val HeartbeatRequestString = "HB"
  val PatternNameRequestString = "PN"
  val UTF_8 = "UTF-8"
}

class LedController(remote: InetSocketAddress) extends Actor with ActorLogging {

  import LedController._
  import Tcp._
  import context._

  implicit val defaultTimeout = Timeout(3 seconds)

  val logger = LoggerFactory.getLogger(getClass)

  var lastHeartbeat = Heartbeat(new DateTime, 0, 0, 0, 0, 0, 0, 0, 0, 0, "")
  var lastPatternNames = PatternNames(List())
  var ledControllerVersion = LedControllerVersion("", "")
  var isOff = false
  var lastPatternSelect = PatternCommand(Some(3), Some(255), Some(255), Some(255), Some(0), Some(0))

  var tickInterval = 5 seconds
  val tickScheduler = context.system.scheduler.schedule (0 milliseconds, tickInterval, self, LedControllerTick)

  def receive = {
    case CommandFailed(_: Connect) =>
      logger.warn("Failed connect to connect to LED Controller")
      context.parent ! NodeConnectionFailed
    case c @ Connected(remote, local) =>
      logger.info("Connected: " + self.path.name)
      val connection = sender
      connection ! Register(self)
      context.system.scheduler.scheduleOnce (10 milliseconds, self, PatternNamesRequest)
      context become connected(connection)
    case LedControllerVersionRequest =>
      context.sender ! LedControllerVersion("<Unknown>", "<Unknown")
    case LedControllerTick =>
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
      val wrapperMessage = FabricProtos.FabricWrapperMessage.parseFrom(data.toArray)
      wrapperMessage.msg match {
        case Msg.Heartbeat(hb) =>
          lastHeartbeat = Heartbeat(new DateTime, hb.messageTypeID.getOrElse(0), hb.versionID.getOrElse(0), hb.currentPattern.getOrElse(0),
            hb.red.getOrElse(0), hb.green.getOrElse(0), hb.blue.getOrElse(0), hb.speed.getOrElse(0), hb.intensity.getOrElse(0),
            hb.memberType.getOrElse(0), hb.currentPatternName.getOrElse(""))
          logger.debug (lastHeartbeat.toString)
        case Msg.PatternNames(pn) =>
          lastPatternNames = PatternNames(pn.name.toList.zipWithIndex.map({case (n, i) => if (n.isEmpty || (i + 1) == OffPatternId) "" else (i + 1).toString + "-" + n}).filter(!_.isEmpty))
          logger.info (lastPatternNames.toString)
        case Msg.Welcome(welcome) =>
          val date =
            try {
              new DateTime(DateTime.parse(welcome.buildTime.getOrElse("").replaceAll("_", "T").replaceAll("-", ":"))).withZone(DateTimeZone.forID("America/Los Angeles"))
            } catch {
              case _: Throwable => new DateTime
            }
          ledControllerVersion = LedControllerVersion(welcome.version.getOrElse(""), ISODateTimeFormat.dateTime.print(date))
          logger.info (ledControllerVersion.toString)
        case Msg.Command(_) =>
        case Msg.PatternCommand(_) =>
        case _ =>
      }
    case LedControllerTick =>
      connection ! Write(MessageHeartbeatRequest)
    case HeartbeatRequest =>
      context.sender ! lastHeartbeat
    case LedControllerVersionRequest =>
      context.sender ! ledControllerVersion
    case PatternNamesRequest =>
      if (lastPatternNames.names.isEmpty) connection ! Write(MessagePatternNamesRequest)
      if (context.sender != self) context.sender ! lastPatternNames
    case select: PatternSelect =>
      if (select.id == OffPatternId) {
        isOff = true
        logger.debug("Off Selected")
        val bytes = ByteString(FabricWrapperMessage.defaultInstance.withPatternCommand(OffCommand).toByteArray)
        connection ! Write(bytes)
      } else if (select.id == -OffPatternId) {
        isOff = false
        logger.debug("Off DeSelected")
        val bytes = ByteString(FabricWrapperMessage.defaultInstance.withPatternCommand(lastPatternSelect).toByteArray)
        connection ! Write(bytes)
      } else {
        lastPatternSelect = PatternCommand(Some(select.id), Some(select.speed), Some(select.intensity), Some(select.red), Some(select.green), Some(select.blue))
        if (isOff) {
          logger.debug("Storing command because we are off")
        } else {
          logger.debug("Sending out command")
          val bytes = ByteString(FabricWrapperMessage.defaultInstance.withPatternCommand(lastPatternSelect).toByteArray)
          connection ! Write(bytes)
        }
      }
    case "close" =>
      logger.info("close")
      connection ! Close
    case _: ConnectionClosed =>
      logger.info("Connection Closed")
      lastHeartbeat = Heartbeat(new DateTime, 0, 0, 0, 0, 0, 0, 0, 0, 0, "")
      lastPatternNames = PatternNames(List())
      context.parent ! NodeConnectionClosed
      context become receive
    case unknown => logger.info("Unknown Message " + unknown.toString)
  }
}
