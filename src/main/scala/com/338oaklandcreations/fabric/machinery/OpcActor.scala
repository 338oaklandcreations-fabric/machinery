package com._338oaklandcreations.fabric.machinery

import akka.actor.{ActorSystem, ActorLogging, Actor, Props}
import com._338oaklandcreations.fabric.opc.OpcClient
import scala.concurrent.duration._
import org.slf4j.LoggerFactory

/**
 * Created by mauricio on 5/30/15.
 */

object OpcActor {
  case class PixelValue(id: Int, color: Int)
  case class Pixel(id: Int)
  case class Clear()
  case class Close()
  case class AnimationRate(frequency: Int)
  case class AnimationTick()
}

class OpcActor extends Actor with ActorLogging {

  implicit val system = ActorSystem()

  import OpcActor._
  import system.dispatcher

  val logger = LoggerFactory.getLogger(getClass)

  logger.info("Starting OpcActor")
  val server = new OpcClient("127.0.0.1", 7890)
  val opcDevice = server.addDevice()
  val strip = opcDevice.addPixelStrip(0, 64)
  strip.setAnimation(new AnimationTestPattern)

  var cancellable = system.scheduler.schedule(0 milliseconds, 50 milliseconds, self, AnimationTick)

  def receive = {
    case AnimationRate(frequency) => {
      cancellable.cancel
      cancellable = system.scheduler.schedule(0 milliseconds, (1.0 / frequency.toDouble) * 1000.0 milliseconds, self, AnimationTick)
      logger.info("Set animation speed to " + frequency + "Hz")
    }
    case AnimationTick => {
      try {
        server.animate
      } catch {
        case e: Exception =>
      }
    }
    case PixelValue(id, color) => {
      strip.setPixelColor(id, color)
      try {
      server.show
      } catch {
        case e: Exception =>
      }
    }
    case Clear => {
      try {
        server.clear
        server.show
      } catch {
        case e: Exception =>
      }
    }
    case Close => {
      try {
        server.close
      } catch {
        case e: Exception =>
      }
    }
  }

}
