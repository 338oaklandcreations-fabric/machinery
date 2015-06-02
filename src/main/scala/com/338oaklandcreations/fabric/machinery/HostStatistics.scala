package org._338oaklandcreations.fabric

import akka.actor.{ Actor, ActorRef, Props, ActorSystem }
import akka.actor.ActorLogging
import scala.concurrent.duration._

import scala.sys.process._

import org.slf4j.{Logger, LoggerFactory}

/** Communication object for monitoring process statistics
  *
  * Collects processor statistics (e.g. CPU, free memory etc) for monitoring
  */
object HostStatistics {
  case class Tick()
  case class TimeSeriesRequestCPU()
  case class TimeSeriesRequestMemory()
  case class TimeSeriesRequestBattery()
  case class MetricHistory(history: List[Double])
}
/** Communication object for monitoring process statistics
  *
  *  @constructor create a new process stats object
  */
class HostStatistics extends Actor with ActorLogging {
  import context._
  import HostStatistics._

  var cpuHistory: List[Double] = List(0.0)
  var memoryHistory: List[Double] = List(0.0)
  var batteryHistory: List[Double] = List(0.0)
  val tickInterval = 5 seconds
  val hoursToTrack = 5 hours
  val tickScheduler = system.scheduler.schedule (0 milliseconds, tickInterval, self, Tick)

  val logger =  LoggerFactory.getLogger(getClass)

  override def preStart(): Unit = {
    logger.info("Starting ProcessStatistics")
  }

  def receive = {
    case TimeSeriesRequestCPU => {
      sender ! MetricHistory(cpuHistory)
    }
    case TimeSeriesRequestMemory => {
      sender ! MetricHistory(memoryHistory)
    }
    case TimeSeriesRequestBattery => {
      sender ! MetricHistory(batteryHistory)
    }
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
