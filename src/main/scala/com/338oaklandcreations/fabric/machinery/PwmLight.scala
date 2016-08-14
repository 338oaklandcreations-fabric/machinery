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

import org.slf4j.LoggerFactory

class PwmLight extends HostActor {

  implicit val logger = LoggerFactory.getLogger(getClass)

  val PwmPeriod = 10000000
  val PwmDevices = List("/sys/devices/ocp.3/bs_pwm_test_P9_14.12", "/sys/devices/ocp.3/bs_pwm_test_P9_16.12", "/sys/devices/ocp.3/bs_pwm_test_P8_19.12")

  def setupPWM = {
    PwmDevices.foreach( { device =>
      setPWMperiod(PwmPeriod, device)
      setPWMduty(0, device)
      setPWMrun(true, device)
    })
  }

  def running: Boolean = {
    false
  }

  def tick = {

  }

  def setPattern(id: Int, level: Int) = {
    PwmDevices.foreach({ device => setPWMduty((level.toDouble / 255.0 * PwmPeriod).toInt, device) })
  }

  setupPWM

}
