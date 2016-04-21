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

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.slf4j.LoggerFactory
import scala.concurrent.Await
import scala.concurrent.duration._

import scala.util.{Failure, Success}

object MachineryController {
}

class MachineryController extends Actor with ActorLogging {

  import HostAPI._
  import LedController._
  import context._

  implicit val defaultTimeout = Timeout(3 seconds)

  val logger = LoggerFactory.getLogger(getClass)

  logger.info("Starting Controller")

  val ledController = actorOf(Props(new LedController(new InetSocketAddress("localhost", 8888))), "ledController")
  val hostAPI = actorOf(Props[HostAPI], "hostAPI")

  def receive = {
    case Pattern(patternId) =>
    case TimeSeriesRequestCPU =>
      logger.debug("TimeSeriesRequestCPU")
      hostAPI forward TimeSeriesRequestCPU
    case TimeSeriesRequestMemory =>
      logger.debug("TimeSeriesRequestMemory")
      hostAPI forward TimeSeriesRequestMemory
    case HostStatisticsRequest =>
      logger.debug("HostStatisticsRequest")
      hostAPI forward HostStatisticsRequest
    case HeartbeatRequest =>
      logger.debug("HeartbeatRequest")
      ledController forward HeartbeatRequest
    case LedPower(on) =>
      logger.debug("LedPower")
      hostAPI forward LedPower(on)
    case LedControllerVersionRequest =>
      logger.debug("LedControllerVersionRequest")
      ledController forward LedControllerVersionRequest
    case PatternNamesRequest =>
      logger.debug("PatternNamesRequest")
      ledController forward PatternNamesRequest
    case NodeConnectionClosed =>
    case unknown => logger.debug("Received Unknown message: " + unknown.toString)
  }

}
