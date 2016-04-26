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

import akka.actor.{Actor, ActorLogging}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.sys.process._

object HostAPI {
  case object HostTick
  case object TimeSeriesRequestCPU
  case object TimeSeriesRequestMemory
  case object HostStatisticsRequest
  case object Reboot

  case class CommandResult(result: Int)
  case class LedPower(on: Boolean)
  case class Settings(newTickInteval: Int, newHoursToTrack: Int)
  case class MetricHistory(history: List[Double])
  case class ConcerningMessages(logfile: String, warn: Int, error: Int, fatal: Int)
  case class HostStatistics(startTime: DateTime, cpuHistory: List[Double], memoryHistory: List[Double], concerning: List[ConcerningMessages])

  val isArm = {
    val hosttype = Process(Seq("bash", "-c", "echo $HOSTTYPE")).!!.replaceAll("\n", "")
    hosttype == "arm"
  }

  val ProcessTimeFormatter = {
    if (isArm) DateTimeFormat.forPattern("H:mm")
    else DateTimeFormat.forPattern("hh:mma")
  }
  val MonthDayTimeFormatter = DateTimeFormat.forPattern("MMMdd")
}

class HostAPI extends Actor with ActorLogging {

  import HostAPI._

  import scala.concurrent.ExecutionContext.Implicits.global

  val logger =  LoggerFactory.getLogger(getClass)

  var cpuHistory: List[Double] = List()
  var memoryHistory: List[Double] = List()
  var startTime: DateTime = null
  var tickInterval = 5 seconds
  var hoursToTrack = 6 hours
  val tickScheduler = context.system.scheduler.schedule (0 milliseconds, tickInterval, self, HostTick)
  val ledPowerPin = "48"
  val ledPowerPinFilename = "/sys/class/gpio/gpio" + ledPowerPin

  override def preStart(): Unit = {
    logger.info("Starting HostAPI...")
    if (isArm) {
      logger.info("Starting GPIO for ledPower control...")
      val enableCommand = "sudo sh -c \"echo " + ledPowerPin + " > /sys/class/gpio/export\""
      logger.info("Enable ledPower pin...")
      Process(Seq("bash", "-c", enableCommand)).!
      val directionCommand = "sudo sh -c \"echo out > " + ledPowerPinFilename + "/direction\""
      logger.info("Direction for ledPower pin...")
      Process(Seq("bash", "-c", directionCommand)).!
      val valueCommand = "sudo sh -c \"echo 0 > "+ ledPowerPinFilename + "/value\""
      logger.info("Value for ledPower pin...")
      Process(Seq("bash", "-c", valueCommand)).!
    }
  }

  def currentCpu: Double = {
    if (isArm) {
      Process("bash" :: "-c" :: "mpstat | awk 'END {print $12}'" :: Nil).!!.toDouble.min(100.0)
    } else {
      Process("bash" :: "-c" :: "ps aux | awk '{sum += $3} END {print sum}'" :: Nil).!!.toDouble.min(100.0)
    }
  }

  def currentMemory: Double = {
    if (isArm) {
      Process("bash" :: "-c" :: "free -m | awk 'FNR==2{print $3 / $2 * 100}'" :: Nil).!!.toDouble.min(100.0)
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
      context.sender ! HostStatistics(startTime, cpuHistory.reverse, memoryHistory.reverse, concerningMessages)
    case LedPower(on) =>
      val pinValue = {
        if (isArm) {
          val value = if (on) "1" else "0"
          val valueCommand = "sudo sh -c \"echo " + value + " > " + ledPowerPinFilename + "/value\""
          Process(Seq("bash", "-c", valueCommand)).!
          val pinValue = ("cat " + ledPowerPinFilename + "/value").!!.replaceAll("\n", "")
          pinValue
        } else {
          if (on) "1" else "0"
        }
      }
      context.sender ! CommandResult(pinValue.toInt)
    case Reboot => CommandResult(Process("sudo reboot").!)
    case HostTick => {
      val latestStartTime = {
        val newStartString = Process("bash" :: "-c" :: "ps aux | grep furSwarm | awk '{if ($11 != \"grep\") {print $9}}'" :: Nil).!!
        if (newStartString.contains("\n")) {
          newStartString.substring(0, newStartString.indexOf("\n")).replaceFirst("^0+(?!$)", "")
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
            startTime = startTime.withDate(monthDayStartTime.getYear, monthDayStartTime.getMonthOfYear, monthDayStartTime.getDayOfMonth)
          case _: Throwable => startTime = new DateTime
        }
      }
      val takeCount: Int = (hoursToTrack / tickInterval).toInt
      cpuHistory = (currentCpu :: cpuHistory).take (takeCount)
      memoryHistory = (currentMemory :: memoryHistory).take (takeCount)
    }
    case x => logger.info ("Unknown Command: " + x.toString())
  }
}
