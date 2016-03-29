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

import org.joda.time.DateTime
import org.joda.time.format.{ISODateTimeFormat, DateTimeFormatter}
import spray.json._

object MachineryJsonProtocol extends DefaultJsonProtocol {

  class GoogleCell(val v: Any) {}
  class GoogleColumn(val id: String, val label: String, val typeName: String) {}
  class GoogleTooltipColumn() extends GoogleColumn("", "", "") {}

  case class GoogleRow(c: List[GoogleCell])
  case class GoogleTable(cols: List[GoogleColumn], rows: List[GoogleRow])

  import LedController._
  import HostAPI._

  implicit object DateJsonFormat extends RootJsonFormat[DateTime] {
    private val parserISO: DateTimeFormatter = ISODateTimeFormat.dateTimeNoMillis()
    private val parserMillisISO: DateTimeFormatter = ISODateTimeFormat.dateTime()
    override def write(obj: DateTime) = JsString(parserISO.print(obj))
    override def read(json: JsValue) : DateTime = json match {
      case JsString(s) =>
        try {
          parserISO.parseDateTime(s)
        } catch {
          case _: Throwable => parserMillisISO.parseDateTime(s)
        }
      case _ => throw new DeserializationException("Error info you want here ...")
    }
  }

  implicit object GoogleCellFormat extends RootJsonFormat[GoogleCell] {
    def write(c: GoogleCell) = c.v match {
      case x: String => JsObject("v" -> JsString(x))
      case x: Int    => JsObject("v" -> JsNumber(x))
      case x: Double => JsObject("v" -> JsNumber(x))
      // TODO: Handle other basic types (e.g. Date)
    }
    def read(value: JsValue) = value match {
      case _ => deserializationError("Undefined Read")
      // TODO: Provide read functionality
    }
  }

  implicit object GoogleColumnFormat extends RootJsonFormat[GoogleColumn] {
    def write(c: GoogleColumn) = {
      c match {
        case x: GoogleTooltipColumn => JsObject("type" -> JsString("string"), "role" -> JsString("tooltip"), "p" -> JsObject("html" -> JsBoolean(true)))
        case _ => JsObject(
          "id" -> JsString(c.id),
          "label" -> JsString(c.label),
          "type" -> JsString(c.typeName) // Required because `type' is a reserved word in Scala
        )
      }
    }
    def read(value: JsValue) = value match {
      case _ => deserializationError("Undefined Read")
      // TODO: Provide read functionality
    }
  }

  // Base case classes
  implicit val pattern = jsonFormat1(Pattern)
  implicit val metricHisotry = jsonFormat1(MetricHistory)
  implicit val commandResult = jsonFormat1(CommandResult)
  implicit val googleRowJSON = jsonFormat1(GoogleRow)
  implicit val googleTableJSON = jsonFormat2(GoogleTable)
}