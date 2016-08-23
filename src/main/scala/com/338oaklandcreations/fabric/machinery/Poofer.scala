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

import java.util.Timer

import org.slf4j.LoggerFactory

object Poofer {
  case class PooferStep(time: Int, states: List[Boolean])

}

class Poofer extends HostActor with TickManager {

  import Poofer._

  implicit val logger = LoggerFactory.getLogger(getClass)

  val timer = new Timer

  val RightPooferPin = "67"
  val LeftPooferPin = "68"

  var runningPattern = 0
  var patternStart = 0L
  var patternStep = 0

  val patterns = {
    List(
      List.empty[PooferStep],
      List(PooferStep(0, List(true, false)),
        PooferStep(100, List(false, false)),
        PooferStep(500, List(false, true)),
        PooferStep(600, List(false, false)),
        PooferStep(1000, List(true, false)),
        PooferStep(1100, List(false, false)),
        PooferStep(1300, List(false, true)),
        PooferStep(1400, List(false, false)),
        PooferStep(2000, List(true, true)),
        PooferStep(2500, List(false, false)),
        PooferStep(3000, List(true, true)),
        PooferStep(3500, List(false, false)),
        PooferStep(4500, List(true, true)),
        PooferStep(5000, List(true, true)),
        PooferStep(5500, List(true, true)),
        PooferStep(6000, List(true, true)),
        PooferStep(6500, List(true, true)),
        PooferStep(8000, List(false, false))
      ),
      List(
        PooferStep(0, List(true, false)),
        PooferStep(50, List(false, true)),
        PooferStep(100, List(true, false)),
        PooferStep(150, List(false, true)),
        PooferStep(200, List(true, false)),
        PooferStep(250, List(false, true)),
        PooferStep(300, List(true, false)),
        PooferStep(350, List(false, true)),
        PooferStep(400, List(true, false)),
        PooferStep(450, List(false, true)),
        PooferStep(500, List(false, false))
      ),
      List(
        PooferStep(0, List(true, false)),
        PooferStep(500, List(false, true)),
        PooferStep(1000, List(true, false)),
        PooferStep(1500, List(false, true)),
        PooferStep(2000, List(true, false)),
        PooferStep(2500, List(false, true)),
        PooferStep(3000, List(true, false)),
        PooferStep(3500, List(false, true)),
        PooferStep(4000, List(true, false)),
        PooferStep(4500, List(false, true)),
        PooferStep(5000, List(false, false))
      )
    )
  }

  def running: Boolean = {
    runningPattern > 0 && patternStep <= patterns(runningPattern).length
  }

  def step = {
    if (runningPattern > 0) {
      logger.info("TEST1")
      val tickTime = System.currentTimeMillis
      if (patternStep >= patterns(runningPattern).length) {
        shutdown
      } else if (patterns(runningPattern)(patternStep).time < tickTime - patternStart) {
        logger.info("Pattern Step: " + patternStep)
        setGPIOpin(patterns(runningPattern)(patternStep).states(0), RightPooferPin)
        setGPIOpin(patterns(runningPattern)(patternStep).states(1), LeftPooferPin)
        patternStep += 1
      }
    }
  }

  def setPattern(id: Int) = {
    shutdown
    if (id < patterns.length) {
      runningPattern = id
      patternStart = System.currentTimeMillis
      patternStep = 0
      tick(() => step, 5L)
    }
  }

  def shutdown = {
    logger.info("Shutdown")
    runningPattern = 0
    patternStep = 0
    setGPIOpin(false, RightPooferPin)
    setGPIOpin(false, LeftPooferPin)
  }

  setupGPIO(RightPooferPin, 0)
  setupGPIO(LeftPooferPin, 0)

}
