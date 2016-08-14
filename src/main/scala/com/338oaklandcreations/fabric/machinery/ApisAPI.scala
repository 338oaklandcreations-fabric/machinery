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

import akka.actor.{Actor, ActorLogging, Cancellable}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object ApisAPI {

  case object ApisTick

  case class PooferPattern(id: Int)
  case class BodyLightPattern(id: Int, level: Int)

}

class ApisAPI extends Actor with ActorLogging {

  import ApisAPI._

  import scala.concurrent.ExecutionContext.Implicits.global

  val logger =  LoggerFactory.getLogger(getClass)

  val TickInterval = 2 milliseconds
  var TickScheduler: Cancellable = null

  val lights = new PwmLight
  val poofers = new Poofer

  def receive = {
    case ApisTick =>
      if (lights.running || poofers.running) {
        lights.tick
        poofers.tick
      } else {
        if (TickScheduler != null) TickScheduler.cancel
        TickScheduler = null
      }
    case PooferPattern(id) =>
      if (TickScheduler == null) TickScheduler = context.system.scheduler.schedule (0 milliseconds, TickInterval, self, ApisTick)
      poofers.setPattern(id)
    case BodyLightPattern(id, level) =>
      if (TickScheduler == null) TickScheduler = context.system.scheduler.schedule (0 milliseconds, TickInterval, self, ApisTick)
      lights.setPattern(id, level)
    case x => logger.info ("Unknown Command: " + x.toString())
  }
}
