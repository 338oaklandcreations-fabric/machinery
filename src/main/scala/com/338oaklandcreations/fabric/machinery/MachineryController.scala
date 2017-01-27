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
import com._338oaklandcreations.fabric.machinery.MachineryController._
import org.joda.time.{DateTimeZone, DateTime}
import org.slf4j.LoggerFactory
import spray.json._

import scala.concurrent.duration._

object MachineryController {

  case object ShutdownCheckTick
  case object SleepCheckTick
  case object SunTimingTick
  case object ExternalMessagesRequest

  case class StartupShutDownTiming(startup: DateTime, shutdown: DateTime)

}

class MachineryController extends Actor with ActorLogging {

  import HostAPI._
  import IotAPI._
  import LedController._
  import LedImageController._
  import SunriseSunset._
  import context._

  implicit val defaultTimeout = Timeout(3 seconds)

  val logger = LoggerFactory.getLogger(getClass)

  logger.info("Starting Controller")

  val ledController = actorOf(Props(new LedController(new InetSocketAddress("127.0.0.1", scala.util.Properties.envOrElse("FABRIC_LED_PORT", "8801").toInt))), "ledController")
  val ledImageController = actorOf(Props(new LedImageController(new InetSocketAddress("127.0.0.1", scala.util.Properties.envOrElse("OPC_SERVER_PORT", "7890").toInt))), "ledImageController")
  val hostAPI = actorOf(Props[HostAPI], "hostAPI")
  val apisAPI = actorOf(Props[ApisAPI], "apisAPI")
  val sunriseSunsetAPI = actorOf(Props[SunriseSunset], "sunriseSunsetAPI")
  val iotAPI = actorOf(Props[IotAPI], "iotAPI")

  var timing = StartupShutDownTiming(new DateTime(2016, 1, 1, 0, 0, DateTimeZone.UTC), new DateTime(2016, 1, 1, 9, 0, DateTimeZone.UTC))

  var externalMessages: List[ExternalMessage] = List()
  var imageController = false

  val shutdownScheduler = context.system.scheduler.schedule (0 milliseconds, 10 minutes, self, ShutdownCheckTick)
  val tickScheduler = context.system.scheduler.schedule (10 seconds, 5 seconds, self, SleepCheckTick)
  val sunTimingTick = context.system.scheduler.schedule (10 seconds, 24 hours, self, SunTimingTick)
  val SwitchoverTime = {
    if (!windflowersHost) 500
    else 750

  }

  val animations = new AnimationCycle

  override def preStart = {
    ledController ! LedControllerConnect(true)
    ledController ! PatternNamesRequest
  }

  def setupController(pattern: PatternSelect) = {
    if (pattern.id >= LedImageController.LowerId) {
      if (!imageController) {
        logger.info("Starting up image controller")
        imageController = true
        ledController ! LedControllerConnect(false)
        Thread.sleep(SwitchoverTime)
        ledImageController ! LedImageControllerConnect(true)
        Thread.sleep(SwitchoverTime)
      }
      ledImageController forward pattern
    } else {
      if (imageController) {
        logger.info("Starting up led controller")
        imageController = false
        ledImageController ! LedImageControllerConnect(false)
        Thread.sleep(SwitchoverTime)
        ledController ! LedControllerConnect(true)
        Thread.sleep(SwitchoverTime)
      }
      ledController forward pattern
    }
  }

  def receive = {
    case sunrise: SunriseSunsetResponse => {
      iotAPI ! ExternalMessage(SunTimingChannel, new DateTime, sunrise.toJson(MachineryJsonProtocol.sunriseSunsetResponse).toString)
      timing = StartupShutDownTiming(sunrise.results.sunset.plusHours(-1).toDateTime(DateTimeZone.UTC), timing.shutdown)
      animations.updateTiming(timing)
      hostAPI ! timing
    }
    case SunTimingTick => sunriseSunsetAPI ! LagunaHills
    case ShutdownCheckTick =>
      if (animations.isShutdown) {
        val offPattern = PatternSelect(LedController.OffPatternId, 0, 0, 0, 0, 0)
        self ! offPattern
        iotAPI ! ExternalMessage(PatternUpdateChannel, new DateTime, offPattern.toJson(MachineryJsonProtocol.patternSelectJson).toString)
      }
    case SleepCheckTick =>
      if (!animations.isShutdown && animations.isSleeping) {
        if (animations.newPatternComing) {
          val currentPattern = animations.currentPattern
          animations.lastAnimationStartTime = System.currentTimeMillis
          val nextPattern = PatternSelect(currentPattern.patternNumber.get, currentPattern.red.get, currentPattern.green.get, currentPattern.blue.get,
            currentPattern.speed.get, currentPattern.intensity.get)
          iotAPI ! ExternalMessage(PatternUpdateChannel, new DateTime, nextPattern.toJson(MachineryJsonProtocol.patternSelectJson).toString)
          logger.warn("Animation Select: " + nextPattern.id)
          setupController(nextPattern)
        }
      }
    case message: ExternalMessage =>
      logger.info("PubNub Message")
      externalMessages = (message :: externalMessages).sortBy(_.timeStamp.getMillis).reverse.take (1000)
    case ExternalMessagesRequest =>
      context.sender ! ExternalMessages(externalMessages)
    case TimeSeriesRequestCPU =>
      logger.info("TimeSeriesRequestCPU")
      hostAPI forward TimeSeriesRequestCPU
    case TimeSeriesRequestMemory =>
      logger.info("TimeSeriesRequestMemory")
      hostAPI forward TimeSeriesRequestMemory
    case HostStatisticsRequest =>
      logger.info("HostStatisticsRequest")
      hostAPI forward HostStatisticsRequest
    case HeartbeatRequest =>
      logger.info("HeartbeatRequest")
      Thread.sleep(SwitchoverTime)
      if (imageController) {
        ledImageController forward HeartbeatRequest
      } else {
        ledController forward HeartbeatRequest
      }
    case LedPower(on) =>
      logger.info("LedPower")
      hostAPI forward LedPower(on)
    case LedControllerVersionRequest =>
      logger.info("LedControllerVersionRequest")
      ledController forward LedControllerVersionRequest
    case PatternNamesRequest =>
      logger.info("PatternNamesRequest")
      ledController forward PatternNamesRequest
    case select: PatternSelect =>
      animations.lastPatternSelectTime = System.currentTimeMillis()
      setupController(select)
    case WellLightSettings(power, level) =>
      logger.info("WellLightSettings")
      hostAPI forward WellLightSettings(power, level)
    case WellLightSettingsRequest =>
      logger.info("WellLightSettingsRequest")
      hostAPI forward WellLightSettingsRequest
    case BodyLightPattern(id, level) =>
      logger.info("BodyLightsPattern")
      apisAPI forward BodyLightPattern(id, level)
    case PooferPattern(id) =>
      logger.info("PooferPattern")
      apisAPI forward PooferPattern(id)
    case NodeConnectionClosed =>
    case unknown => logger.debug("Received Unknown message: " + unknown.toString)
  }

}
