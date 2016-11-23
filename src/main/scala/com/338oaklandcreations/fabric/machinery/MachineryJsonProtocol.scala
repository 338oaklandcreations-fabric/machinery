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

import ApisAPI.{BodyLightPattern, PooferPattern}
import HostAPI._
import LedController._
import SunriseSunset._
import IotAPI._
import com._338oaklandcreations.fabric.machinery.MachineryController.StartupShutDownTiming
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}
import spray.json._

object MachineryJsonProtocol extends DefaultJsonProtocol {

  case class ServerVersion(version: String, scalaVersion: String, builtAt: String)

  implicit object DateJsonFormat extends RootJsonFormat[DateTime] {
    private val parserISO: DateTimeFormatter = ISODateTimeFormat.dateTimeNoMillis()
    private val parserMillisISO: DateTimeFormatter = ISODateTimeFormat.dateTime()

    override def write(obj: DateTime) = JsString(parserISO.print(obj))

    override def read(json: JsValue): DateTime = json match {
      case JsString(s) =>
        try {
          parserISO.parseDateTime(s)
        } catch {
          case _: Throwable => parserMillisISO.parseDateTime(s)
        }
      case _ => throw new DeserializationException("Error info you want here ...")
    }
  }

  // Base case classes

  implicit val serverVersionJson = jsonFormat3(ServerVersion)
  implicit val metricHistoryJson = jsonFormat1(MetricHistory)
  implicit val concerningMessagesJson = jsonFormat4(ConcerningMessages)
  implicit val startupShutdownTimingJson = jsonFormat2(StartupShutDownTiming)
  implicit val hostStatisticsJson = jsonFormat5(HostStatistics)
  implicit val commandResultJson = jsonFormat1(CommandResult)

  implicit val heartbeatJson = jsonFormat11(Heartbeat)
  implicit val patternNamesJson = jsonFormat1(PatternNames)
  implicit val patternSelectJson = jsonFormat6(PatternSelect)
  implicit val patternJson = jsonFormat1(Pattern)
  implicit val ledControllerVersionJson = jsonFormat2(LedControllerVersion)
  implicit val wellLightSettingsJson = jsonFormat2(WellLightSettings)

  implicit val bodyLightsJson = jsonFormat2(BodyLightPattern)
  implicit val pooferJson = jsonFormat1(PooferPattern)

  implicit val sunriseSunsetResults = jsonFormat10(SunriseSunsetResults)
  implicit val sunriseSunsetResponse = jsonFormat2(SunriseSunsetResponse)
  implicit val externalMessage = jsonFormat3(ExternalMessage)
  implicit val externalMessagesJ = jsonFormat1(ExternalMessages)

}