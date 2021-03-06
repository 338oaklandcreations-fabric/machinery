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

import java.io.File

import com.github.tototoshi.csv._

import com._338oaklandcreations.fabric.machinery.FabricProtos.PatternCommand
import org.joda.time.{DateTimeZone, DateTime}
import HostAware._
import org.slf4j.LoggerFactory

object AnimationCycle {

  val logger = LoggerFactory.getLogger(getClass)

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

  val PatternNameMap = Map((0x03 -> "FS_ID_FULL_COLOR"),
    (0x04 -> "FS_ID_SPARKLE"),
    (0x05 -> "FS_ID_DESCEND"),
    (0x06 -> "FS_ID_OFF"),
    (0x07 -> "FS_ID_FLASH"),
    (0x09 -> "FS_ID_HEART"),
    (0x0a -> "FS_ID_BREATHE"),
    (0x0b -> "FS_ID_ORGANIC"),
    (0x0c -> "FS_ID_CYLON"),
    (0x0d -> "FS_ID_DROP"),
    (0x0e -> "FS_ID_CHARACTER"),
    (0x0f -> "FS_ID_CYLON_VERTICAL"),
    (0x10 -> "FS_ID_CYLON_PONG"),
    (0x11 -> "FS_ID_BREATHE_EVOLVE"),
    (0x16 -> "FS_ID_PRISM"),
    (0x18 -> "FS_ID_MATRIX"),
    (0x19 -> "FS_ID_RAINBOW_CHASE"),
    (0x1a -> "FS_ID_RANDOM_FLASH"),
    (0x1c -> "FS_ID_STARFIELD"),
    (0x25 -> "FS_ID_FOREST_RUN"),

    (1000 -> "FS_ID_FLAMES_IMAGE"),
    (1001 -> "FS_ID_SEAHORSE_IMAGE"),
    (1002 -> "FS_ID_SPARKLE_IMAGE"),
    (1003 -> "FS_ID_UNDERWATER_IMAGE"),
    (1004 -> "FS_ID_BLUE_WAVE_IMAGE"),
    (1005 -> "FS_ID_GOLD_BUBBLES_IMAGE"),
    (1006 -> "FS_ID_GRAPE_SUNSET_IMAGE"),
    (1007 -> "FS_ID_PURPLE_BUBBLES_IMAGE"),
    (1008 -> "FS_ID_NARROW_FLAME"),
    (1009 -> "FS_ID_FLOWER_FLICKER"))

  val PatternNameReverseMap = {
    PatternNameMap.map(_.swap)
  }

  val SleepThreshold = 1 * 60 * 1000

  def animationsFromFile(filename: String): List[(Long, PatternCommand)] = {
    val reader = CSVReader.open(new File(filename))
    val cycle = reader.allWithHeaders.map { fields => {
      (fields("time").toLong, PatternCommand(Some(PatternNameReverseMap(fields("pattern_name"))),
        Some(fields("speed").toInt), Some(fields("intensity").toInt), Some(fields("red").toInt), Some(fields("green").toInt), Some(fields("blue").toInt)))
    }}
    logger.warn("Read animation cycle from " + filename)
    cycle
  }

  val Steps: List[(Long, PatternCommand)] = {
    if (reedsHost) {
      logger.warn("Loading reeds animation cycle")
      try {
        animationsFromFile("~/animationPatterns/reeds_animation_cycle.csv")
      } catch {
        case x: Throwable =>
          List(
            // Pattern, speed, intensity, red, green, blue
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(50), Some(55), Some(100), Some(0), Some(120))),
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(50), Some(55), Some(0), Some(100), Some(120))),
            //(15 * 60 * 1000L, PatternCommand(Some(FS_ID_UNDERWATER_IMAGE), Some(244), Some(255), Some(100), Some(0), Some(120))),
            //(15 * 60 * 1000L, PatternCommand(Some(FS_ID_GOLD_BUBBLES_IMAGE), Some(254), Some(255), Some(100), Some(0), Some(120))),
            //(15 * 60 * 1000L, PatternCommand(Some(FS_ID_GRAPE_SUNSET_IMAGE), Some(240), Some(255), Some(100), Some(0), Some(120))),
            //(15 * 60 * 1000L, PatternCommand(Some(FS_ID_SEAHORSE_IMAGE), Some(229), Some(255), Some(100), Some(0), Some(120))),
            //(15 * 60 * 1000L, PatternCommand(Some(FS_ID_NARROW_FLAME), Some(242), Some(255), Some(100), Some(0), Some(120))),
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(22), Some(99), Some(0), Some(250), Some(89))),
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_ORGANIC), Some(24), Some(255), Some(110), Some(255), Some(254))),
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_RAINBOW_CHASE), Some(120), Some(255), Some(255), Some(255), Some(255))),
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_CYLON), Some(125), Some(255), Some(254), Some(184), Some(139))),
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_CYLON), Some(115), Some(255), Some(100), Some(200), Some(200)))
          )
      }
    } else if (windflowersHost) {
      logger.warn("Loading windflowers animation cycle")
      try {
        animationsFromFile("~/animationPatterns/windflowers_animation_cycle.csv")
      } catch {
        case x: Throwable =>
          List(
            // Pattern, speed, intensity, red, green, blue
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(96), Some(58), Some(195), Some(174), Some(0))), // Persimmon
            (15 * 60 * 1000L, PatternCommand(Some(FS_ID_SPARKLE_IMAGE), Some(255), Some(255), Some(128), Some(128), Some(65))),
            (10 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(96), Some(58), Some(195), Some(174), Some(0))), // Yellow
            (10 * 60 * 1000L, PatternCommand(Some(FS_ID_CYLON), Some(59), Some(255), Some(250), Some(162), Some(4))),
            (10 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(57), Some(56), Some(199), Some(198), Some(73))), // Champagne
            (15 * 60 * 1000L, PatternCommand(Some(FS_ID_GRAPE_SUNSET_IMAGE), Some(255), Some(255), Some(128), Some(128), Some(128))),
            (10 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(96), Some(79), Some(212), Some(116), Some(255))), // Purple
            (15 * 60 * 1000L, PatternCommand(Some(FS_ID_UNDERWATER_IMAGE), Some(255), Some(255), Some(68), Some(128), Some(128))),
            (15 * 60 * 1000L, PatternCommand(Some(FS_ID_NARROW_FLAME), Some(255), Some(255), Some(128), Some(128), Some(128))),
            (10 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(96), Some(79), Some(107), Some(0), Some(255))), // Blue Purple
            (15 * 60 * 1000L, PatternCommand(Some(FS_ID_NARROW_FLAME), Some(255), Some(255), Some(128), Some(128), Some(128))),
            (15 * 60 * 1000L, PatternCommand(Some(FS_ID_GOLD_BUBBLES_IMAGE), Some(255), Some(255), Some(128), Some(128), Some(128))),
            (10 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(57), Some(56), Some(0), Some(255), Some(145))), // Teal
            (15 * 60 * 1000L, PatternCommand(Some(FS_ID_FLOWER_FLICKER), Some(255), Some(255), Some(128), Some(128), Some(128))), // Orange and faster
            (10 * 60 * 1000L, PatternCommand(Some(FS_ID_CYLON), Some(59), Some(255), Some(214), Some(200), Some(72)))
          )
      }
    } else {
      logger.warn("Loading generic animation cycle")
      try {
        animationsFromFile("~/animationPatterns/generic_animation_cycle.csv")
      } catch {
        case x: Throwable =>
          List(
            // Pattern, speed, intensity, red, green, blue
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(96), Some(58), Some(195), Some(174), Some(0))),  // Persimmon
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_SPARKLE_IMAGE), Some(255), Some(255), Some(128), Some(128), Some(65))),
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(96), Some(58), Some(195), Some(174), Some(0))),  // Yellow
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_CYLON), Some(59), Some(255), Some(250), Some(162), Some(4))),
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(57), Some(56), Some(199), Some(198), Some(73))),  // Champagne
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_GRAPE_SUNSET_IMAGE), Some(255), Some(255), Some(128), Some(128), Some(128))),
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(96), Some(79), Some(212), Some(116), Some(255))),  // Purple
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_UNDERWATER_IMAGE), Some(255), Some(255), Some(68), Some(128), Some(128))),
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_NARROW_FLAME), Some(255), Some(255), Some(128), Some(128), Some(128))),
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(96), Some(79), Some(107), Some(0), Some(255))),  // Blue Purple
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_NARROW_FLAME), Some(255), Some(255), Some(128), Some(128), Some(128))),
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_GOLD_BUBBLES_IMAGE), Some(255), Some(255), Some(128), Some(128), Some(128))),
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(57), Some(56), Some(0), Some(255), Some(145))),  // Teal
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_FLOWER_FLICKER), Some(255), Some(255), Some(128), Some(128), Some(128))),  // Orange and faster
            (1 * 60 * 1000L, PatternCommand(Some(FS_ID_CYLON), Some(59), Some(255), Some(214), Some(200), Some(72)))
            /*
          // Pattern, speed, intensity, red, green, blue
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(96), Some(58), Some(195), Some(174), Some(0))),  // Persimmon
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_SPARKLE_IMAGE), Some(255), Some(255), Some(128), Some(128), Some(65))),
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(96), Some(58), Some(195), Some(174), Some(0))),  // Yellow
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_CYLON), Some(59), Some(255), Some(250), Some(162), Some(4))),
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(57), Some(56), Some(199), Some(198), Some(73))),  // Champagne
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_GRAPE_SUNSET_IMAGE), Some(255), Some(255), Some(128), Some(128), Some(128))),
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(96), Some(79), Some(212), Some(116), Some(255))),  // Purple
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_UNDERWATER_IMAGE), Some(255), Some(255), Some(68), Some(128), Some(128))),
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_NARROW_FLAME), Some(255), Some(255), Some(128), Some(128), Some(128))),
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(96), Some(79), Some(107), Some(0), Some(255))),  // Blue Purple
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_NARROW_FLAME), Some(255), Some(255), Some(128), Some(128), Some(128))),
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_GOLD_BUBBLES_IMAGE), Some(255), Some(255), Some(128), Some(128), Some(128))),
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_BREATHE), Some(57), Some(56), Some(0), Some(255), Some(145))),  // Teal
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_FLOWER_FLICKER), Some(255), Some(255), Some(128), Some(128), Some(128))),  // Orange and faster
          (2 * 60 * 1000L, PatternCommand(Some(FS_ID_CYLON), Some(59), Some(255), Some(214), Some(200), Some(72)))
          */
          )
      }
    }
  }
}

class AnimationCycle {

  import AnimationCycle._
  import SunriseSunset._
  import MachineryController._

  var currentStep = 0
  var nextTime = 0L
  var lastPatternSelectTime = System.currentTimeMillis
  var lastAnimationStartTime = 0L
  var sunriseSunsetResponse: SunriseSunsetResponse = null
  var timing = StartupShutDownTiming(new DateTime(2016, 1, 1, 0, 0, DateTimeZone.UTC), new DateTime(2016, 1, 1, 9, 0, DateTimeZone.UTC))

  def updateTiming(updatedTiming: StartupShutDownTiming) = {
    timing = updatedTiming
  }

  def isShutdown: Boolean = {
    val current = new DateTime(DateTimeZone.UTC)
    if (timing.shutdown.getHourOfDay > timing.startup.getHourOfDay) {
      (current.getHourOfDay >= timing.shutdown.getHourOfDay || current.getHourOfDay < timing.startup.getHourOfDay) && !developmentHost
    } else {
      (current.getHourOfDay >= timing.shutdown.getHourOfDay || current.getHourOfDay < (timing.startup.getHourOfDay - 24)) && !developmentHost
    }
  }

  def isSleeping: Boolean = {
    System.currentTimeMillis - lastPatternSelectTime > SleepThreshold
  }

  def newPatternComing: Boolean = {
    System.currentTimeMillis - lastAnimationStartTime > currentPatternTime
  }

  def currentPatternTime: Long = {
    Steps(currentStep)._1
  }

  def currentPattern: PatternCommand = {
    if (System.currentTimeMillis - lastAnimationStartTime > currentPatternTime) incrementStep
    Steps(currentStep)._2
  }

  def incrementStep = {
    currentStep += 1
    if (currentStep >= Steps.length) currentStep = 0
  }

}
