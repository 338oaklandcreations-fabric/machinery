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

import java.io.{FileWriter, BufferedWriter}

import akka.actor.{Actor, ActorLogging}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory
import scala.util.Properties._

import scala.concurrent.duration._
import scala.sys.process._

object HostAPI {
  case object Tick
  case object TimeSeriesRequestCPU
  case object TimeSeriesRequestMemory
  case object HostStatisticsRequest
  case object Reboot
  case object Shutdown

  case class CommandResult(result: Int)
  case class LedPower(on: Boolean)
  case class Settings(newTickInteval: Int, newHoursToTrack: Int)
  case class MetricHistory(history: List[Double])
  case class HostStatistics(startTime: DateTime, cpuHistory: List[Double], memoryHistory: List[Double])

  val ProcessTimeFormatter = DateTimeFormat.forPattern("hh:mma")
}

class HostAPI extends Actor with ActorLogging {

  import HostAPI._
  import scala.concurrent.ExecutionContext.Implicits.global

  val logger =  LoggerFactory.getLogger(getClass)

  var cpuHistory: List[Double] = List()
  var memoryHistory: List[Double] = List()
  var startTime = ""
  var tickInterval = 5 seconds
  var hoursToTrack = 5 hours
  val tickScheduler = context.system.scheduler.schedule (0 milliseconds, tickInterval, self, Tick)
  val ledPowerPin = "48"
  val ledPowerPinFilename = "/sys/class/gpio/gpio" + ledPowerPin
  val isArm = {
    val hosttype = Process("bash" :: "-c" :: "echo $HOSTTYPE" :: Nil).!!.replaceAll("\n", "")
    logger.info("HOSTTYPE=" + hosttype)
    hosttype == "arm"
  }

  override def preStart(): Unit = {
    logger.info("Starting HostAPI...")
    if (isArm) {
      logger.info("Starting GPIO for ledPower control...")
      val exportPin = Process("bash" :: "-c" :: "sudo sh -c \"echo " + ledPowerPin + " > /sys/class/gpio/export\"" :: Nil).!!
      logger.info("Export pin:    " + exportPin)
      val setDirection = Process("bash" :: "-c" :: "sudo sh -c \"echo out > " + ledPowerPinFilename + "/direction\"" :: Nil).!!
      logger.info("Set direction: " + setDirection)
      val setValue = Process("bash" :: "-c" :: "sudo sh -c \"echo 0 > "+ ledPowerPinFilename + "/value\"" :: Nil).!!
      logger.info("Set value:     " + setValue)
    }
  }

  def receive = {
    case Settings(newTickInterval, newHoursToTrack) =>
      tickInterval = newTickInterval seconds;
      hoursToTrack = newHoursToTrack hours
    case TimeSeriesRequestCPU => context.sender ! MetricHistory(cpuHistory.reverse)
    case TimeSeriesRequestMemory => context.sender ! MetricHistory(memoryHistory.reverse)
    case HostStatisticsRequest =>
      logger.info("HostStatisticsRequest " + startTime)
      val startTimeDate = ProcessTimeFormatter.parseDateTime(startTime)
      logger.info("HostStatisticsRequest2")
      context.sender ! HostStatistics(startTimeDate, cpuHistory.reverse, memoryHistory.reverse)
    case LedPower(on) =>
      val pinValue = {
        if (isArm) {
          val pinFile = new BufferedWriter(new FileWriter(ledPowerPinFilename + "/value"))
          if (on) pinFile.write("1")
          else pinFile.write("0")
          pinFile.close
          val pinValue = Process("bash" :: "-c" :: "cat " + ledPowerPinFilename + "/value" :: Nil).!!
          logger.info("Pin value = " + pinValue)
          pinValue
        } else {
          if (on) "1" else "0"
        }
      }
      context.sender ! CommandResult(pinValue.toInt)
    case Shutdown => CommandResult(Process("sudo shutdown").!)
    case Reboot => CommandResult(Process("sudo reboot").!)
    case Tick => {
      val cpuCount = Process("bash" :: "-c" :: "ps aux | awk '{sum += $3} END {print sum}'" :: Nil).!!
      val cpuCountDouble: Double = cpuCount.toDouble.min(100.0)
      val memoryCount = Process("bash" :: "-c" :: "ps aux | awk '{sum += $4} END {print sum}'" :: Nil).!!
      val memoryCountDouble: Double = memoryCount.toDouble.min(100.0)
      startTime = Process("bash" :: "-c" :: "ps aux | grep furSwarm | awk '{if ($11 != \"grep\") {print $9}}'" :: Nil).!!
      logger.info("tick " + startTime)
      if (startTime.contains("\n")) {
        startTime = startTime.substring(0, startTime.indexOf("\n"))
      }
      logger.info("tick2 " + startTime)
      val takeCount: Int = (hoursToTrack / tickInterval).toInt
      cpuHistory = (cpuCountDouble :: cpuHistory).take (takeCount)
      memoryHistory = (memoryCountDouble :: memoryHistory).take (takeCount)
    }
    case x => logger.info ("Unknown Command: " + x.toString())
  }
}
