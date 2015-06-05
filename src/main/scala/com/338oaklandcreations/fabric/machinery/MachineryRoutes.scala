package com._338oaklandcreations.fabric.machinery

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.slf4j.LoggerFactory
import spray.routing._
import spray.json._
import DefaultJsonProtocol._
import spray.http._
import MediaTypes._

import scala.concurrent._
import scala.concurrent.duration._

trait MachineryRoutes extends HttpService {

  import HostStatistics._
  import OpcActor._

  implicit val defaultTimeout = Timeout(10 seconds)

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
      path("hostMemory") {
        val future = controller ? TimeSeriesRequestMemory
        val message: List[Double] = Await.result(future.mapTo[List[Double]], defaultTimeout.duration)
        respondWithMediaType(`application/json`) {
          complete(message.toJson.toString)
        }
      } ~
      path("hostCPU") {
        val future = controller ? TimeSeriesRequestCPU
        val message: List[Double] = Await.result(future.mapTo[List[Double]], defaultTimeout.duration)
        respondWithMediaType(`application/json`) {
          complete(message.toJson.toString)
        }
      } ~
      path("hostBattery") {
        val future = controller ? TimeSeriesRequestBattery
        val message: List[Double] = Await.result(future.mapTo[List[Double]], defaultTimeout.duration)
        respondWithMediaType(`application/json`) {
          complete(message.toJson.toString)
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
