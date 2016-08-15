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

import org.slf4j.Logger

import scala.sys.process.Process

trait HostActor {

  val isArm = {
    val hosttype = Process(Seq("bash", "-c", "echo $HOSTTYPE")).!!.replaceAll("\n", "")
    hosttype == "arm"
  }

  def pinFilename(pin: String) = "/sys/class/gpio/gpio" + pin

  def setupGPIO(pin: String, initialValue: Int)(implicit logger: Logger) = {
    if (isArm) {
      val enableCommand = "sudo sh -c \"echo " + pin + " > /sys/class/gpio/export\""
      logger.info("Enable ledPower pin...")
      Process(Seq("bash", "-c", enableCommand)).!
      val directionCommand = "sudo sh -c \"echo out > " + pinFilename(pin) + "/direction\""
      logger.info("Direction for ledPower pin...")
      Process(Seq("bash", "-c", directionCommand)).!
      val valueCommand = "sudo sh -c \"echo " + initialValue + " > "+ pinFilename(pin) + "/value\""
      logger.info("Value for ledPower pin...")
      Process(Seq("bash", "-c", valueCommand)).!
    }
  }

  def setGPIOpin(pinValue: Boolean, pin: String): String = {
    if (isArm) {
      val value = if (pinValue) "1" else "0"
      val valueCommand = "sudo sh -c \"echo " + value + " > " + pinFilename(pin) + "/value\""
      Process(Seq("bash", "-c", valueCommand)).!
      val setPinResult = Process("cat " + pinFilename(pin) + "/value").!!.replaceAll("\n", "")
      setPinResult
    } else if (pinValue) "1" else "0"
  }
  def setPWMperiod(period: Int, device: String)(implicit logger: Logger) = {
    if (isArm) {
      val periodCommand = "sudo sh -c \"echo " + period + " > " + device + "/period\""
      logger.info("Set PWM period to " + period + "...")
      Process(Seq("bash", "-c", periodCommand)).!
    }
  }

  def setPWMduty(duty: Int, device: String)(implicit logger: Logger) = {
    if (isArm) {
      val dutyCommand = "sudo sh -c \"echo " + duty + " > " + device + "/duty\""
      //logger.info("Set PWM duty to " + duty + "...")
      Process(Seq("bash", "-c", dutyCommand)).!
    }
  }

  def setPWMrun(on: Boolean, device: String)(implicit logger: Logger) = {
    if (isArm) {
      val value = if (on) "1" else "0"
      val enableCommand = "sudo sh -c \"echo " + value + " > " + device + "/run\""
      logger.info("Set PWM pin run to " + value + "...")
      Process(Seq("bash", "-c", enableCommand)).!
    }
  }

  def pwmLogarithmicLevel(level: Int, period: Int): Int = {
    ((Math.pow(10.0, (level - 1).toDouble / (253.0 / 3.0)) / 1030.0).max(0.0).min(1.0) * period.toDouble).toInt
  }

  def pwmCommandLevel(level: Int): Int = {
    (Math.log10(1030.0 * level) * (253.0 / 3.0) + 1.0).max(0.0).min(1.0).toInt
  }

  def PwmStep(level: Int): Int = {
    ((level * Math.log(10) / 80 * 2).toInt).max(15000)
  }

}
