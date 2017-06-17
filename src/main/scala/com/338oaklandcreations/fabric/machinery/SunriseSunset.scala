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

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import spray.can.Http
import spray.http.{HttpMethods, HttpRequest, HttpResponse}
import spray.httpx.ResponseTransformation._
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._

object SunriseSunset {

  val CallTimeout = 30 seconds

  val Rockridge = Location(37.850407,-122.2560957)
  val LagunaHills = Location(33.581079, -117.7346472)

  case class Location(latitude: Double, longitude: Double)
  case class SunriseSunsetResults(sunrise: org.joda.time.DateTime, sunset: DateTime, solar_noon: DateTime, day_length: Int,
                                  civil_twilight_begin: DateTime, civil_twilight_end: DateTime,
                                  nautical_twilight_begin: DateTime, nautical_twilight_end: DateTime,
                                  astronomical_twilight_begin: DateTime, astronomical_twilight_end: DateTime)
  case class SunriseSunsetResponse(results: SunriseSunsetResults, status: String)
}

class SunriseSunset extends Actor with ActorLogging {

  import SunriseSunset._

  val logger =  LoggerFactory.getLogger(getClass)
  implicit val system = ActorSystem()
  implicit val timeout: Timeout = Timeout(5 minutes)

  val ApiHost = "api.sunrise-sunset.org"
  var currentTiming = SunriseSunsetResponse(
    SunriseSunsetResults(DateTime.now, DateTime.now, DateTime.now, 0,
      DateTime.now, DateTime.now, DateTime.now,
      DateTime.now, DateTime.now, DateTime.now),
    "")

  def uri(location: Location): String = {
    "http://" + ApiHost + "/json?lat=" + location.latitude + "&lng=" + location.longitude + "&formatted=0"
  }

  def receive = {
    case location: Location =>
      try {
        val hostConnector = Await.result (IO(Http) ? Http.HostConnectorSetup(ApiHost), CallTimeout) match {
          case Http.HostConnectorInfo(hostConnector, _) => hostConnector
        }
        val request = HttpRequest(HttpMethods.GET, uri(location))
        val response: String = Await.result ((hostConnector ? request).mapTo[HttpResponse], CallTimeout) ~> unmarshal[String]
        currentTiming = response.parseJson.convertTo[SunriseSunsetResponse](MachineryJsonProtocol.sunriseSunsetResponse)
        sender ! currentTiming
        logger.warn("Sun timing updated: " + currentTiming)
      } catch {
        case x: Throwable => logger.error("Could not update sun timing: " + x)
      }
    case x => sender ! "UNKNOWN REQUEST TYPE: " + x.toString
  }

}

