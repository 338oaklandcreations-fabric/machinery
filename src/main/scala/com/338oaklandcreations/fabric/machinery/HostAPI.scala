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

import akka.actor.{Actor, ActorLogging, Cancellable}
import org.joda.time.{DateTimeZone, DateTime}
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.sys.process._

object HostAPI {

  import MachineryController._

  case object HostTick
  case object WellLightTick
  case object TimeSeriesRequestCPU
  case object TimeSeriesRequestMemory
  case object HostStatisticsRequest
  case object WellLightSettingsRequest
  case object Reboot

  case class CommandResult(result: Int)
  case class LedPower(on: Boolean)
  case class WellLightSettings(powerOn: Boolean, level: Int)
  case class Settings(newTickInteval: Int, newHoursToTrack: Int)
  case class MetricHistory(history: List[Double])
  case class ConcerningMessages(logfile: String, warn: Int, error: Int, fatal: Int)
  case class HostStatistics(startTime: DateTime, cpuHistory: List[Double], memoryHistory: List[Double],
                            concerning: List[ConcerningMessages], timing: StartupShutDownTiming)

  val MonthDayTimeFormatter = DateTimeFormat.forPattern("MMMdd")
}

class HostAPI extends Actor with ActorLogging with HostActor {

  import HostAPI._
  import MachineryController._

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val logger = LoggerFactory.getLogger(getClass)

  val ProcessTimeFormatter = {
    if (isArm) DateTimeFormat.forPattern("H:mm")
    else DateTimeFormat.forPattern("hh:mma")
  }

  var cpuHistory: List[Double] = List()
  var memoryHistory: List[Double] = List()
  var dataReturnHistory: List[Int] = List()
  var startTime: DateTime = null
  var timing = StartupShutDownTiming(new DateTime(2016, 1, 1, 0, 0, DateTimeZone.UTC), new DateTime(2016, 1, 1, 9, 0, DateTimeZone.UTC))
  var wellLightSettings: WellLightSettings = null
  var currentWellLightSettings: WellLightSettings = null
  var tickInterval = 5 seconds
  var hoursToTrack = 6 hours
  val PwmPeriod = 10000000
  val tickScheduler = context.system.scheduler.schedule (0 milliseconds, tickInterval, self, HostTick)
  val wellLightTickInterval = 2 milliseconds
  var wellLightTickScheduler: Cancellable = null
  val ledPowerPin = "48"
  val dataReturnPin = "60"
  val PwmDevice = "/sys/devices/ocp.3/bs_pwm_test_P9_14.12"

  def setupPWM = {
    setPWMperiod(PwmPeriod, PwmDevice)
    setPWMduty(0, PwmDevice)
    setPWMrun(false, PwmDevice)
  }

  override def preStart(): Unit = {
    logger.info("Starting HostAPI...")
    logger.info("Starting GPIO for ledPower control...")
    setupGPIO(ledPowerPin, "out", 1)
    logger.info("Starting GPIO for ledPowerPin control...")
    setupGPIO(dataReturnPin, "in", 0)
    logger.info("Starting PWM for wellLight control...")
    setupPWM
    wellLightSettings = WellLightSettings(false, 128)
    currentWellLightSettings = WellLightSettings(false, 128)
  }

  def currentCpu: Double = {
    if (isArm) {
      100.0 - Process("bash" :: "-c" :: "mpstat | awk 'END {print $12}'" :: Nil).!!.toDouble.min(100.0)
    } else {
      Process("bash" :: "-c" :: "ps aux | awk '{sum += $3} END {print sum}'" :: Nil).!!.toDouble.min(100.0)
    }
  }

  def currentMemory: Double = {
    if (isArm) {
      Process("bash" :: "-c" :: "free -m | awk 'FNR==3{print $3 / ($4 + $3) * 100}'" :: Nil).!!.toDouble.min(100.0)
    } else {
      Process("bash" :: "-c" :: "ps aux | awk '{sum += $4} END {print sum}'" :: Nil).!!.toDouble.min(100.0)
    }
  }

  def concerningMessages: List[ConcerningMessages] = {
    if (isArm) {
      List("furSwarmLinux.log", "opcServer.log", "machinery.log").map { file =>
        val warn = Process("bash" :: "-c" :: "grep 'WARN' " + file + " | awk 'END{print NR}'" :: Nil).!!.replaceAll("\n", "").toInt
        val error = Process("bash" :: "-c" :: "grep 'ERROR' " + file + " | awk 'END{print NR}'" :: Nil).!!.replaceAll("\n", "").toInt
        val fatal = Process("bash" :: "-c" :: "grep 'FATAL' " + file + " | awk 'END{print NR}'" :: Nil).!!.replaceAll("\n", "").toInt
        ConcerningMessages(file, warn, error, fatal)
      }
    } else List(ConcerningMessages("furSwarmLinux.log", 0, 0, 0), ConcerningMessages("opcServer.log", 0, 0, 0), ConcerningMessages("machinery.log", 0, 0, 0))
  }

  def receive = {
    case Settings(newTickInterval, newHoursToTrack) =>
      tickInterval = newTickInterval seconds;
      hoursToTrack = newHoursToTrack hours
    case TimeSeriesRequestCPU => context.sender ! MetricHistory(cpuHistory.reverse)
    case TimeSeriesRequestMemory => context.sender ! MetricHistory(memoryHistory.reverse)
    case HostStatisticsRequest =>
      context.sender ! HostStatistics(startTime, cpuHistory.reverse, memoryHistory.reverse, concerningMessages, timing)
    case LedPower(on) =>
      val pinValue = setGPIOpin(on, ledPowerPin)
      context.sender ! CommandResult(pinValue.toInt)
    case updatedTiming: StartupShutDownTiming =>
      timing = updatedTiming
    case WellLightSettings(power, level) =>
      val pinValue = {
        if (isArm) {
          setPWMrun(power, PwmDevice)
          if (power) {
            val value = ((level.toDouble / 255.0) * PwmPeriod).toInt
            value
          } else {
            0
          }
        } else {
          level
        }
      }
      wellLightSettings = WellLightSettings(power, pwmLogarithmicLevel(level, PwmPeriod))
      if (wellLightTickScheduler != null) wellLightTickScheduler.cancel
      wellLightTickScheduler = context.system.scheduler.schedule (0 milliseconds, wellLightTickInterval, self, WellLightTick)
    case WellLightTick =>
      if (wellLightSettings.level > currentWellLightSettings.level) {
        val newLevel = {
          if (currentWellLightSettings.level + PwmStep(currentWellLightSettings.level) > wellLightSettings.level) wellLightSettings.level
          else currentWellLightSettings.level + PwmStep(currentWellLightSettings.level)
        }
        currentWellLightSettings = WellLightSettings(currentWellLightSettings.powerOn, newLevel)
        setPWMduty(currentWellLightSettings.level, PwmDevice)
        //logger.info("Dimming at " + currentWellLightSettings.level.toString + " toward " + wellLightSettings.level)
      } else if (wellLightSettings.level < currentWellLightSettings.level) {
        val newLevel = {
          if (currentWellLightSettings.level - PwmStep(currentWellLightSettings.level) < wellLightSettings.level) wellLightSettings.level
          else currentWellLightSettings.level - PwmStep(currentWellLightSettings.level)
        }
        currentWellLightSettings = WellLightSettings(currentWellLightSettings.powerOn, newLevel)
        setPWMduty(currentWellLightSettings.level, PwmDevice)
        //logger.info("Dimming at " + currentWellLightSettings.level.toString + " toward " + wellLightSettings.level)
      } else {
        logger.info("Stop well light dimming")
        if (wellLightTickScheduler != null) wellLightTickScheduler.cancel
        wellLightTickScheduler = null
      }
    case WellLightSettingsRequest =>
      context.sender ! WellLightSettings(wellLightSettings.powerOn, pwmCommandLevel(wellLightSettings.level))
    case Reboot => CommandResult(Process("sudo reboot").!)
    case HostTick => {
      val latestStartTime = {
        val newStartString = Process("bash" :: "-c" :: "ps aux | grep furSwarm | awk '{if ($11 != \"grep\") {print $9}}'" :: Nil).!!
        if (newStartString.contains("\n")) {
          newStartString.substring(0, newStartString.indexOf("\n")).replaceFirst("^0(?!$)", "")
        } else newStartString
      }
      if (startTime == null || ProcessTimeFormatter.print(startTime) != latestStartTime) {
        try {
          val processStartTime = ProcessTimeFormatter.parseDateTime(latestStartTime)
          startTime = (new DateTime).withTime(processStartTime.getHourOfDay, processStartTime.getMinuteOfHour,
            processStartTime.getSecondOfMinute, processStartTime.getMillisOfSecond)
        } catch {
          case e: IllegalArgumentException =>
            val monthDayStartTime = MonthDayTimeFormatter.parseDateTime(latestStartTime)
            if (startTime == null) new DateTime
            else startTime = startTime.withDate(monthDayStartTime.getYear, monthDayStartTime.getMonthOfYear, monthDayStartTime.getDayOfMonth)
          case _: Throwable => startTime = new DateTime
        }
      }
      val takeCount: Int = (hoursToTrack / tickInterval).toInt
      cpuHistory = (currentCpu :: cpuHistory).take (takeCount)
      memoryHistory = (currentMemory :: memoryHistory).take (takeCount)
      dataReturnHistory = (getGPIOpin(dataReturnPin) :: dataReturnHistory).take (12)
      if (!dataReturnHistory.exists(_ != dataReturnHistory.head)) {
        if (scala.util.Properties.envOrElse("FABRIC_DATA_HISTORY_REPORT", "False").toBoolean) {
          logger.warn("No change in dataReturnHistory")
        }
      }

    }
    case x => logger.info ("Unknown Command: " + x.toString())
  }
}
