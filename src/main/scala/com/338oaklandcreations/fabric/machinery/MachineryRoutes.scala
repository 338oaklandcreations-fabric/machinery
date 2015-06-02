package com._338oaklandcreations.fabric.machinery

import spray.can.Http
import spray.can.server.UHttp
import spray.routing._
import spray.json._
import spray.http._
import MediaTypes._
import DefaultJsonProtocol._
import scala.concurrent._
import akka.actor.{ ActorRef }
import akka.pattern.ask
import org.slf4j.{ Logger, LoggerFactory }

trait MachineryRoutes extends HttpService {

  import OpcActor._

  val logger = LoggerFactory.getLogger(getClass)

  val controller: ActorRef

  val machineryRoutes = {
    getFromResourceDirectory("webapp") ~
      path("pixel" / IntNumber / "([0-9A-F]{6})".r) { (pixelId, color) =>
        controller ! PixelValue(pixelId, Integer.parseInt(color, 16))
        complete {
          pixelId + color
        }
      } ~
      path("pixel" / IntNumber) { pixelId =>
        complete {
          pixelId.toString
        }
      } ~
      path("animationSpeed" / IntNumber) { frequency =>
        controller ! AnimationRate(frequency)
        complete {
          frequency.toString
        }
      } ~
      path("clear") { ctx =>
        controller ! Clear
        ctx.complete {
          "cleared"
        }
      } ~
      path("shutdown") { ctx =>
        ctx.complete {
          "shutdown"
        }
      } ~
      path("reboot") { ctx =>
        ctx.complete {
          "reboot"
        }
      }
  }
}
