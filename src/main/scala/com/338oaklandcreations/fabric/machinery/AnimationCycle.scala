/*

    Copyright = C) 2016 Mauricio Bustos = m@bustos.org) & 338.oakland creations

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    = at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

*/

package com._338oaklandcreations.fabric.machinery

import com._338oaklandcreations.fabric.machinery.FabricProtos.PatternCommand
import org.joda.time.DateTime

object AnimationCycle extends HostAware {

  val FS_ID_FULL_COLOR = 0x03
  val FS_ID_SPARKLE = 0x04
  val FS_ID_DESCEND = 0x05
  val FS_ID_OFF = 0x06
  val FS_ID_FLASH = 0x07
  val FS_ID_HEART = 0x09
  val FS_ID_BREATHE = 0x0a
  val FS_ID_ORGANIC = 0x0b
  val FS_ID_CYLON = 0x0c
  val FS_ID_DROP = 0x0d
  val FS_ID_CHARACTER = 0x0e
  val FS_ID_CYLON_VERTICAL = 0x0f
  val FS_ID_CYLON_PONG = 0x10
  val FS_ID_BREATHE_EVOLVE = 0x11
  val FS_ID_PRISM = 0x16
  val FS_ID_MATRIX = 0x18
  val FS_ID_RAINBOW_CHASE = 0x19
  val FS_ID_RANDOM_FLASH = 0x1a
  val FS_ID_STARFIELD = 0x1c
  val FS_ID_FOREST_RUN = 0x25

  val FS_ID_FLAMES_IMAGE = 1000
  val FS_ID_SEAHORSE_IMAGE = 1001
  val FS_ID_SPARKLE_IMAGE = 1002
  val FS_ID_UNDERWATER_IMAGE = 1003
  val FS_ID_BLUE_WAVE_IMAGE = 1004
  val FS_ID_GOLD_BUBBLES_IMAGE = 1005
  val FS_ID_GRAPE_SUNSET_IMAGE = 1006
  val FS_ID_PURPLE_BUBBLES_IMAGE = 1007
  val FS_ID_NARROW_FLAME = 1008
  val FS_ID_FLOWER_FLICKER = 1009

  val ShutdownTime = new DateTime(2016, 1, 1, 14, 0)
  val StartupTime = new DateTime(2016, 1, 1, 0, 0)
  val SleepThreshold = 10 * 60 * 1000

  val Steps: List[(Long, PatternCommand)] = {
    if (reedsHost) {
      List(
        // Pattern, speed, intensity, red, green, blue
        (15 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(50), Some(55), Some(100), Some(0), Some(120))),
        (15 * 60 * 1000L, PatternCommand(Some(FS_ID_UNDERWATER_IMAGE), Some(244), Some(255), Some(100), Some(0), Some(120))),
        (15 * 60 * 1000L, PatternCommand(Some(FS_ID_GOLD_BUBBLES_IMAGE), Some(254), Some(255), Some(100), Some(0), Some(120))),
        (15 * 60 * 1000L, PatternCommand(Some(FS_ID_GRAPE_SUNSET_IMAGE), Some(240), Some(255), Some(100), Some(0), Some(120))),
        (15 * 60 * 1000L, PatternCommand(Some(FS_ID_SEAHORSE_IMAGE), Some(229), Some(255), Some(100), Some(0), Some(120))),
        (15 * 60 * 1000L, PatternCommand(Some(FS_ID_FLAMES_IMAGE), Some(242), Some(255), Some(100), Some(0), Some(120))),
        (15 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(22), Some(99), Some(0), Some(250), Some(89))),
        (15 * 60 * 1000L, PatternCommand(Some(FS_ID_ORGANIC), Some(24), Some(255), Some(110), Some(255), Some(254))),
        (15 * 60 * 1000L, PatternCommand(Some(FS_ID_CYLON), Some(125), Some(255), Some(254), Some(184), Some(139)))
      )
    } else if (windflowersHost) {
      List.empty[(Long, PatternCommand)]
    } else List.empty[(Long, PatternCommand)]
  }
}

class AnimationCycle extends HostAware {

  import AnimationCycle._

  var currentStep = 0
  var nextTime = 0L
  var lastPatternSelectTime = 0L
  var lastAnimationStartTime = 0L

  def isShutdown: Boolean = {
    val current = new DateTime
    if (ShutdownTime.getHourOfDay > StartupTime.getHourOfDay) {
      current.getHourOfDay >= ShutdownTime.getHourOfDay && current.getHourOfDay < StartupTime.getHourOfDay && !developmentHost
    } else {
      current.getHourOfDay >= ShutdownTime.getHourOfDay && (current.getHourOfDay < StartupTime.getHourOfDay + 24) && !developmentHost
    }
  }

  def isSleeping: Boolean = {
    System.currentTimeMillis - lastPatternSelectTime > SleepThreshold
  }

  def newPatternComing: Boolean = {
    System.currentTimeMillis() - lastAnimationStartTime > currentPatternTime
  }

  def currentPatternTime: Long = {
    Steps(currentStep)._1
  }

  def currentPattern: PatternCommand = {
    if (System.currentTimeMillis() - lastAnimationStartTime > currentPatternTime) incrementStep
    Steps(currentStep)._2
  }

  def incrementStep = {
    currentStep += 1
    if (currentStep >= Steps.length) currentStep = 0
  }

}
