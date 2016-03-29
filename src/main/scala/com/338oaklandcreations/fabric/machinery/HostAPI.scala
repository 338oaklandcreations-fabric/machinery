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
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.sys.process._

/** Communication object for monitoring process statistics
  *
  * Collects processor statistics (e.g. CPU, free memory etc) for monitoring
  */
object HostAPI {
  case object Tick
  case object TimeSeriesRequestCPU
  case object TimeSeriesRequestMemory
  case object TimeSeriesRequestBattery
  case object Reboot
  case object Shutdown

  case class CommandResult(result: Int)
  case class Settings(newTickInteval: Int, newHoursToTrack: Int)
  case class MetricHistory(history: List[Double])
}
/** Communication object for monitoring process statistics
  *
  *  @constructor create a new process stats object
  */
class HostAPI extends Actor with ActorLogging {

  import HostAPI._
  import context._

  var cpuHistory: List[Double] = List(0.0)
  var memoryHistory: List[Double] = List(0.0)
  var batteryHistory: List[Double] = List(0.0)
  var tickInterval = 5 seconds
  var hoursToTrack = 5 hours
  val tickScheduler = system.scheduler.schedule (0 milliseconds, tickInterval, self, Tick)

  val logger =  LoggerFactory.getLogger(getClass)

  override def preStart(): Unit = {
    logger.info("Starting ProcessStatistics")
  }

  def receive = {
    case Settings(newTickInterval, newHoursToTrack) => {
      tickInterval = newTickInterval seconds;
      hoursToTrack = newHoursToTrack hours;
    }
    case TimeSeriesRequestCPU => {
      sender ! MetricHistory(cpuHistory)
    }
    case TimeSeriesRequestMemory => {
      sender ! MetricHistory(memoryHistory)
    }
    case TimeSeriesRequestBattery => {
      sender ! MetricHistory(batteryHistory)
    }
    case Shutdown => CommandResult(Process("sudo shutdown").!)
    case Reboot => CommandResult(Process("sudo reboot").!)
    case Tick => {
      val cpuCount = Process("bash" :: "-c" :: "ps aux | awk '{sum += $3} END {print sum}'" :: Nil).!!
      val cpuCountDouble: Double = cpuCount.toDouble
      val memoryCount = Process("bash" :: "-c" :: "ps aux | awk '{sum += $4} END {print sum}'" :: Nil).!!
      val memoryCountDouble: Double = memoryCount.toDouble
      val takeCount: Int = (hoursToTrack / tickInterval).toInt
      cpuHistory = (cpuCountDouble :: cpuHistory).take (takeCount)
      memoryHistory = (memoryCountDouble :: memoryHistory).take (takeCount)
    }
    case x => {
      logger.info ("Unknown Command: " + x.toString())
    }
  }
}
