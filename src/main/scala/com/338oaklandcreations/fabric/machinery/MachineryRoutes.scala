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

import java.net.InetAddress

import akka.actor._
import akka.pattern.ask
import org.slf4j.LoggerFactory
import spray.http.DateTime
import spray.http.MediaTypes._
import spray.http.StatusCodes._
import spray.json._
import spray.routing._

import scala.util.{Failure, Success}

class MachineryRoutesServiceActor extends HttpServiceActor with ActorLogging {

  override def actorRefFactory = context

  val machineryRoutes = new MachineryRoutes {
    def actorRefFactory = context
  }

  def receive = runRoute(machineryRoutes.routes)

}

trait MachineryRoutes extends HttpService with UserAuthentication {

  import HostAPI._
  import LedController._
  import UserAuthentication._
  import MachineryJsonProtocol._


  val logger = LoggerFactory.getLogger(getClass)
  val system = ActorSystem("fabricSystem")

  import system.dispatcher

  val controller = system.actorOf(Props[MachineryController], "MachineryController")

  val routes =
    setPattern ~
      getHostMemory ~
      getHostCPU ~
      getHeartbeat ~
      getHostStatistics ~
      ledPower ~
      shutdown ~
      reboot

  val authenticationRejection = RejectionHandler {
    case AuthenticationRejection(message) :: _ => complete(400, message)
  }

  val authorizationRejection = RejectionHandler {
    case AuthenticationRejection(message) :: _ => complete(400, message)
  }

  val secureCookies: Boolean = {
    // Don't require HTTPS if running in development
    val hostname = InetAddress.getLocalHost.getHostName
    hostname != "localhost" && !hostname.contains("pro")
  }

  def redirectToHttps: Directive0 = {
    requestUri.flatMap { uri =>
      redirect(uri.copy(scheme = "https"), MovedPermanently)
    }
  }

  val isHttpsRequest: RequestContext => Boolean = { ctx =>
    (ctx.request.uri.scheme == "https" || ctx.request.headers.exists(h => h.is("x-forwarded-proto") && h.value == "https")) && secureCookies
  }

  def enforceHttps: Directive0 = {
    extract(isHttpsRequest).flatMap(
      if (_) pass
      else redirectToHttps
    )
  }

  val keyLifespanMillis = 120000 * 1000 // 2000 minutes
  val expiration = DateTime.now + keyLifespanMillis
  val SessionKey = "FABRIC_SESSION"
  val UserKey = "FABRIC_USER"
  val ResponseTextHeader = "{\"responseText\": "

  def setPattern = post {
    path("pattern" / IntNumber) { (patternId) =>
      respondWithMediaType(`application/json`) { ctx =>
        val future = controller ? Pattern(patternId)
        future onComplete {
          case Success(Pattern(pid)) => ctx.complete(Pattern(pid).toJson.toString)
          case Failure(x) => ctx.complete(400, x.toString)
          case _ => ctx.complete(400, ResponseTextHeader + "\"Unknown command results\"}")
        }
      }
    }
  }
  def getHostMemory = get {
    path("hostMemory") {
      respondWithMediaType(`application/json`) { ctx =>
        val future = controller ? TimeSeriesRequestMemory
        future onComplete {
          case Success(success) => success match {
            case history: MetricHistory => ctx.complete(history.toJson.toString)
            case _ => ctx.complete(400, ResponseTextHeader + "\"Unknown command results\"}")
          }
          case Failure(failure) => ctx.complete(400, failure.toString)
        }
      }
    }
  }
  def getHostCPU = get {
    path("hostCPU") {
      respondWithMediaType(`application/json`) { ctx =>
        val future = controller ? TimeSeriesRequestCPU
        future onComplete {
          case Success(success) => success match {
            case history: MetricHistory => ctx.complete(history.toJson.toString)
            case _ => ctx.complete(400, ResponseTextHeader + "\"Unknown command results\"}")
          }
          case Failure(failure) => ctx.complete(400, failure.toString)
        }
      }
    }
  }
  def getHeartbeat = get {
    path("heartbeat") {
      respondWithMediaType(`application/json`) { ctx =>
        val future = controller ? HeartbeatRequest
        future onComplete {
          case Success(success) => success match {
            case heartbeat: Heartbeat => ctx.complete(heartbeat.toJson(jsonFormat10(Heartbeat)).toString)
            case _ => ctx.complete(400, ResponseTextHeader + "\"Unknown command results\"}")
          }
          case Failure(failure) => ctx.complete(400, failure.toString)
        }
      }
    }
  }
  def getHostStatistics = get {
    path("hostStatistics") {
      respondWithMediaType(`application/json`) { ctx =>
        val future = controller ? HostStatisticsRequest
        future onComplete {
          case Success(success) => success match {
            case heartbeat: HostStatistics => ctx.complete(heartbeat.toJson.toString)
            case _ => ctx.complete(400, ResponseTextHeader + "\"Unknown command results\"}")
          }
          case Failure(failure) => ctx.complete(400, failure.toString)
        }
      }
    }
  }
  def ledPower = post {
    path("ledPower" / """(on|off)""".r) { (select) =>
      respondWithMediaType(`application/json`) { ctx =>
        val future = controller ? LedPower(select == "on")
        future onComplete {
          case Success(success) => success match {
            case result: CommandResult => ctx.complete(result.toJson.toString)
            case _ => ctx.complete(400, ResponseTextHeader + "\"Unknown command results\"}")
          }
          case Failure(failure) => ctx.complete(400, failure.toString)
        }
      }
    }
  }
  def shutdown = post {
    path("shutdown") { ctx =>
      val future = controller ? Shutdown
      future onComplete {
        case Success(success) => success match {
          case response: CommandResult => ctx.complete(response.toJson.toString)
          case _ => ctx.complete(400, ResponseTextHeader + "\"Unknown command results\"}")
        }
        case Failure(failure) => ctx.complete(400, failure.toString)
      }
    }
  }
  def reboot = post {
    path("reboot") { ctx =>
      val future = controller ? Reboot
      future onComplete {
        case Success(success) => success match {
          case response: CommandResult => ctx.complete(response.toJson.toString)
          case _ => ctx.complete(400, ResponseTextHeader + "\"Unknown command results\"}")
        }
        case Failure(failure) => ctx.complete(400, failure.toString)
      }
    }
  }
}
