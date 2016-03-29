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

import java.net.InetSocketAddress

import akka.actor._
import org.slf4j.LoggerFactory

object MachineryController {
}

class MachineryController extends Actor with ActorLogging {

  import HostAPI._
  import LedController._
  import context._

  val logger = LoggerFactory.getLogger(getClass)

  logger.info("Starting Controller")

  val ledController = actorOf(Props(new LedController(new InetSocketAddress("0.0.0.0", 2590), self)), "ledController")
  val hostAPI = actorOf(Props[HostAPI], "hostAPI")

  def receive = {
    case Pattern(patternId) =>
    case TimeSeriesRequestCPU =>
    case TimeSeriesRequestMemory =>
    case TimeSeriesRequestBattery =>
    case _ => logger.debug("Received Unknown message")
  }

}
