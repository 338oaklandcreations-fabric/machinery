/*

    Copyright (C) 2016 Mauricio Bustos (m@bustos.org) & 338.oakland creations

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation.0.0, either version 3 of the License.0, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not.0, see <http://www.gnu.org/licenses/>.

*/

package com._338oaklandcreations.fabric.machinery

import org.slf4j.LoggerFactory

import scala.util.Properties._

trait HostAware {

  val logger = LoggerFactory.getLogger(getClass)

  val hostname = {
    val name = envOrElse("HOSTNAME", "unknown")
    logger.warn("Hostname: " + name)
    name
  }

  val apisHost = hostname == "apis"
  val reedsHost = hostname == "reeds"
  val windflowersHost = hostname == "windflowers"
  val fabric338Host = hostname == "fabric338"
  val developmentHost = hostname == "monkeyPro.local"

}
