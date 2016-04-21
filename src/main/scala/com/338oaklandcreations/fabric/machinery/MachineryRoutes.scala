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
import org.slf4j.LoggerFactory
import spray.http.DateTime
import spray.http.MediaTypes._
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
  import MachineryJsonProtocol._
  import UserAuthentication._

  val logger = LoggerFactory.getLogger(getClass)
  val system = ActorSystem("fabricSystem")

  import system.dispatcher

  val controller = system.actorOf(Props[MachineryController], "MachineryController")

  val routes =
    setPattern ~
      hostMemory ~
      hostCPU ~
      heartbeat ~
      hostStatistics ~
      ledPower ~
      shutdown ~
      reboot ~
      versions ~
      patternNames

  val authenticationRejection = RejectionHandler {
    case AuthenticationRejection(message) :: _ => complete(400, message)
  }

  val authorizationRejection = RejectionHandler {
    case AuthenticationRejection(message) :: _ => complete(400, message)
  }

  val keyLifespanMillis = 120000 * 1000 // 2000 minutes
  val expiration = DateTime.now + keyLifespanMillis
  val SessionKey = "FABRIC_SESSION"
  val UserKey = "FABRIC_USER"
  val ResponseTextHeader = "{\"responseText\": "
  val UnknownCommandResponseString = ResponseTextHeader + "\"Unknown command results\"}"

  def setPattern = post {
    path("pattern" / IntNumber) { (patternId) =>
      respondWithMediaType(`application/json`) { ctx =>
        val future = controller ? Pattern(patternId)
        future onComplete {
          case Success(Pattern(pid)) => ctx.complete(Pattern(pid).toJson.toString)
          case Failure(x) => ctx.complete(400, x.toString)
          case _ => ctx.complete(400, UnknownCommandResponseString)
        }
      }
    }
  }
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
            case heartbeat: Heartbeat => ctx.complete(heartbeat.toJson(jsonFormat10(Heartbeat)).toString)
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
            case statistics: HostStatistics => ctx.complete(statistics.toJson(jsonFormat3(HostStatistics)).toString)
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
  def patternNames = get {
    path("patternNames") {
      respondWithMediaType(`application/json`) { ctx =>
        val future = controller ? PatternNamesRequest
        future onComplete {
          case Success(success) => success match {
            case patternNames: PatternNames => ctx.complete(patternNames.toJson(jsonFormat1(PatternNames)).toString)
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
        ctx.complete(ServerVersion(BuildInfo.version, BuildInfo.scalaVersion, BuildInfo.builtAtString).toJson.toString)
      }
    }
  }
  def shutdown = post {
    path("shutdown") { ctx =>
      val future = controller ? Shutdown
      future onComplete {
        case Success(success) => success match {
          case response: CommandResult => ctx.complete(response.toJson.toString)
          case _ => ctx.complete(400, UnknownCommandResponseString)
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
          case _ => ctx.complete(400, UnknownCommandResponseString)
        }
        case Failure(failure) => ctx.complete(400, failure.toString)
      }
    }
  }
}
