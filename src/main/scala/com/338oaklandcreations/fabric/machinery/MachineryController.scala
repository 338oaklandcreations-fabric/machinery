package com._338oaklandcreations.fabric.machinery

import akka.actor._
import org._338oaklandcreations.fabric.HostStatistics
import org.slf4j.LoggerFactory

object MachineryController {
}

class MachineryController extends Actor with ActorLogging {

  import MachineryConsole._
  import OpcActor._
  import context._

  val logger = LoggerFactory.getLogger(getClass)

  logger.info("Starting Controller")

  val consoleInput = actorOf(Props[MachineryConsole], "machineryConsole")
  val opcClient = actorOf(Props[OpcActor], "opcController")
  val hostStats = actorOf(Props[HostStatistics], "hostStatistics")

  def receive = {
    case AnimationRate(frequency) => opcClient ! AnimationRate(frequency)
    case PixelValue(id, color) => opcClient ! PixelValue(id, color)
    case Clear => opcClient ! Clear
    case _ => logger.debug("Received Unknown message")
  }

}
