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

import akka.actor._
import akka.pattern.ask
import org.joda.time.format.{ISODateTimeFormat, DateTimeFormat}
import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.LoggerFactory
import spray.http.HttpCookie
import spray.http.MediaTypes._
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

  import spray.json._
  import HostAPI._
  import LedController._
  import MachineryJsonProtocol._
  import UserAuthentication._

  val logger = LoggerFactory.getLogger(getClass)
  val system = ActorSystem("fabricSystem")

  import system.dispatcher

  val controller = system.actorOf(Props[MachineryController], "MachineryController")

  val routes =
    hostMemory ~
      hostCPU ~
      heartbeat ~
      hostStatistics ~
      ledPower ~
      reboot ~
      versions ~
      setPattern ~
      patternNames

  val authenticationRejection = RejectionHandler {
    case AuthenticationRejection(message) :: _ => complete(400, message)
  }

  def cookies = cookie("FABRIC_GUIDANCE_SESSION") & cookie("FABRIC_GUIDANCE_USER") & respondWithMediaType(`application/json`)
  def authenticateCookies(sessionId: HttpCookie, username: HttpCookie) = authenticate(authenticateSessionId(sessionId.content, username.content))

  val ResponseTextHeader = "{\"responseText\": "
  val UnknownCommandResponseString = ResponseTextHeader + "\"Unknown command results\"}"

  def hostMemory = get {
    path("hostMemory") {
      respondWithMediaType(`application/json`) { ctx =>
        val future = controller ? TimeSeriesRequestMemory
        future onComplete {
          case Success(success) => success match {
            case history: MetricHistory => ctx.complete(history.toJson.toString)
            case _ => ctx.complete(400, UnknownCommandResponseString)
          }
          case Failure(failure) => ctx.complete(400, failure.toString)
        }
      }
    }
  }
  def hostCPU = get {
    path("hostCPU") {
      respondWithMediaType(`application/json`) { ctx =>
        val future = controller ? TimeSeriesRequestCPU
        future onComplete {
          case Success(success) => success match {
            case history: MetricHistory => ctx.complete(history.toJson.toString)
            case _ => ctx.complete(400, UnknownCommandResponseString)
          }
          case Failure(failure) => ctx.complete(400, failure.toString)
        }
      }
    }
  }
  def heartbeat = get {
    path("heartbeat") {
      respondWithMediaType(`application/json`) { ctx =>
        val future = controller ? HeartbeatRequest
        future onComplete {
          case Success(success) => success match {
            case heartbeat: Heartbeat => ctx.complete(heartbeat.toJson.toString)
            case _ => ctx.complete(400, UnknownCommandResponseString)
          }
          case Failure(failure) => ctx.complete(400, failure.toString)
        }
      }
    }
  }
  def hostStatistics = get {
    path("hostStatistics") {
      respondWithMediaType(`application/json`) { ctx =>
        val future = controller ? HostStatisticsRequest
        future onComplete {
          case Success(success) => success match {
            case statistics: HostStatistics => {
              println(statistics)
              ctx.complete(statistics.toJson.toString)
            }
            case _ => ctx.complete(400, UnknownCommandResponseString)
          }
          case Failure(failure) => ctx.complete(400, failure.toString)
        }
      }
    }
  }
  def ledPower = post {
    path("ledPower" / """(on|off)""".r) { (select) =>
      respondWithMediaType(`application/json`) { ctx =>
        if (select == "off") controller ! PatternSelect(LedController.OffPatternId, 0, 0, 0, 0, 0)
        else controller ! PatternSelect(-LedController.OffPatternId, 0, 0, 0, 0, 0)
        val future = controller ? LedPower(select == "on")
        future onComplete {
          case Success(success) => success match {
            case result: CommandResult => ctx.complete(result.toJson.toString)
            case _ => ctx.complete(400, UnknownCommandResponseString)
          }
          case Failure(failure) => ctx.complete(400, failure.toString)
        }
      }
    }
  }
  def setPattern = post {
    path("pattern") {
      respondWithMediaType(`application/json`) { ctx =>
        val patternSelect = ctx.request.entity.data.asString.parseJson.convertTo[PatternSelect]
        controller ! patternSelect
        ctx.complete(CommandResult(0).toJson.toString)
      }
    }
  }
  def patternNames = get {
    path("pattern" / "names") {
      respondWithMediaType(`application/json`) { ctx =>
        val future = controller ? PatternNamesRequest
        future onComplete {
          case Success(success) => success match {
            case patternNamesResult: PatternNames => ctx.complete(patternNamesResult.toJson.toString)
            case _ => ctx.complete(400, UnknownCommandResponseString)
          }
          case Failure(failure) => ctx.complete(400, failure.toString)
        }
      }
    }
  }
  def versions = get {
    pathPrefix("version") {
      path("ledController") { ctx =>
        controller ? LedControllerVersionRequest onComplete {
          case Success(success) => success match {
            case response: LedControllerVersion => ctx.complete(response.toJson.toString)
            case _ => ctx.complete(400, UnknownCommandResponseString)
          }
          case Failure(failure) => ctx.complete(400, failure.toString)
        }
      } ~ path("server") { ctx =>
        val date: DateTime = new DateTime(BuildInfo.builtAtMillis).withZone(DateTimeZone.UTC)
        ctx.complete(ServerVersion(BuildInfo.version, BuildInfo.scalaVersion, ISODateTimeFormat.dateTime.print(date)).toJson.toString)
      }
    }
  }
  def reboot = post {
    path("reboot") { ctx =>
      val future = controller ? Reboot
      future onComplete {
        case Success(success) => success match {
          case response: CommandResult => ctx.complete(response.toJson.toString)
          case _ => ctx.complete(400, UnknownCommandResponseString)
        }
        case Failure(failure) => ctx.complete(400, failure.toString)
      }
    }
  }
}
