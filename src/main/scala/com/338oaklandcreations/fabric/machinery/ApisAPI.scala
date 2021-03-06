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

import akka.actor.{Actor, ActorLogging}
import org.slf4j.LoggerFactory

object ApisAPI {

  case object ApisTick

  case class PooferPattern(id: Int)
  case class BodyLightPattern(id: Int, level: Int)

}

class ApisAPI extends Actor with ActorLogging {

  import ApisAPI._

  val logger =  LoggerFactory.getLogger(getClass)

  val lights = new PwmLight
  val poofers = new Poofer

  def receive = {
    case ApisTick =>
    case PooferPattern(id) =>
      poofers.setPattern(id)
    case BodyLightPattern(id, level) =>
      lights.setPattern(id, level)
    case x => logger.info ("Unknown Command: " + x.toString())
  }
}
