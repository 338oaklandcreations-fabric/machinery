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
import akka.util.Timeout
import com._338oaklandcreations.fabric.machinery.ApisAPI.{BodyLightPattern, PooferPattern}
import com._338oaklandcreations.fabric.machinery.MachineryController.SleepCheckTick
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object MachineryController {

  case object SleepCheckTick

}

class MachineryController extends Actor with ActorLogging {

  import HostAPI._
  import LedController._
  import LedImageController._
  import context._

  implicit val defaultTimeout = Timeout(3 seconds)

  val logger = LoggerFactory.getLogger(getClass)

  logger.info("Starting Controller")

  val ledController = actorOf(Props(new LedController(new InetSocketAddress("localhost", scala.util.Properties.envOrElse("FABRIC_LED_PORT", "8801").toInt))), "ledController")
  val ledImageController = actorOf(Props(new LedImageController(new InetSocketAddress("localhost", scala.util.Properties.envOrElse("OPC_SERVER_PORT", "7890").toInt))), "ledImageController")
  val hostAPI = actorOf(Props[HostAPI], "hostAPI")
  val apisAPI = actorOf(Props[ApisAPI], "apisAPI")

  var lastPatternSelectTime = 0L
  var imageController = false

  val tickScheduler = context.system.scheduler.schedule (0 milliseconds, 60 seconds, self, SleepCheckTick)
  val animations = new AnimationCycle

  override def preStart = {
    ledController ! LedControllerConnect(true)
  }

  def receive = {
    case SleepCheckTick =>
      if (System.currentTimeMillis() - lastPatternSelectTime > AnimationCycle.SleepThreshold) {
        
      }
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
      Thread.sleep(1000)
      if (imageController) {
        ledImageController forward HeartbeatRequest
      } else {
        ledController forward HeartbeatRequest
      }
    case LedPower(on) =>
      logger.debug("LedPower")
      hostAPI forward LedPower(on)
    case LedControllerVersionRequest =>
      logger.debug("LedControllerVersionRequest")
      ledController forward LedControllerVersionRequest
    case PatternNamesRequest =>
      logger.debug("PatternNamesRequest")
      ledController forward PatternNamesRequest
    case select: PatternSelect =>
      logger.debug("PatternSelect")
      lastPatternSelectTime = System.currentTimeMillis()
      if (select.id >= LedImageController.LowerId) {
        imageController = true
        ledController ! LedControllerConnect(false)
        Thread.sleep(500)
        ledImageController ! LedImageControllerConnect(true)
        Thread.sleep(500)
        ledImageController forward select
      } else {
        imageController = false
        ledImageController ! LedImageControllerConnect(false)
        Thread.sleep(500)
        ledController ! LedControllerConnect(true)
        Thread.sleep(500)
        ledController forward select
      }
    case WellLightSettings(power, level) =>
      logger.debug("WellLightSettings")
      hostAPI forward WellLightSettings(power, level)
    case WellLightSettingsRequest =>
      logger.debug("WellLightSettingsRequest")
      hostAPI forward WellLightSettingsRequest
    case BodyLightPattern(id, level) =>
      logger.debug("BodyLightsPattern")
      apisAPI forward BodyLightPattern(id, level)
    case PooferPattern(id) =>
      logger.debug("PooferPattern")
      apisAPI forward PooferPattern(id)
    case NodeConnectionClosed =>
    case unknown => logger.debug("Received Unknown message: " + unknown.toString)
  }

}
