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

import java.awt.image.BufferedImage
import java.net.InetSocketAddress
import javax.imageio.ImageIO

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object LedImageController extends HostActor with HostAware {

  def props(remote: InetSocketAddress) = Props(classOf[LedImageController], remote)

  case object FrameTick
  case object ConnectionTick

  case class LedImageControllerConnect(var connect: Boolean)
  case class Image(height: Int, width: Int, image: BufferedImage)
  case class Point(point: List[Double])

  val ConnectionTickInterval = 5 seconds
  val TickInterval = {
    if (windflowersHost) 100 milliseconds
    else 12 milliseconds
  }

  val SpeedModifier = 1
  val PixelHop = {
    if (windflowersHost) 15
    else 5
  }

  val LedCount = {
    if (apisHost) 101
    else if (reedsHost) ReedsPlacement.positions.length
    else if (windflowersHost) 280 //WindflowersPlacement.positions.length
    else WindflowersPlacement.positions.length
  }

  val LedCountList = (0 to LedCount - 1).toList
  val NumBytes = LedCount * 3
  val LowerId = 1000
  var PatternNames = List.empty[String]

}

class LedImageController(remote: InetSocketAddress) extends Actor with ActorLogging {

  import LedController.{Heartbeat, HeartbeatRequest, PatternSelect}
  import LedImageController._
  import Tcp._
  import context._

  val logger =  LoggerFactory.getLogger(getClass)
  logger.info(hostname)

  val enableConnect = LedImageControllerConnect(false)
  val tickScheduler = context.system.scheduler.schedule (0 milliseconds, TickInterval, self, FrameTick)
  val connectScheduler = context.system.scheduler.schedule (0 milliseconds, ConnectionTickInterval, self, ConnectionTick)

  val layout: LedPlacement = {
    if (apisHost) ApisPlacement
    else if (reedsHost) ReedsPlacement
    else if (windflowersHost) WindflowersPlacement
    else WindflowersPlacement
  }

  val pixelPositions: List[(Double, Double)] = layout.positions

  def horizontalPixelSpacing = currentImage.width / layout.layoutWidth

  var globalCursor = (0, 0)
  var direction = 1
  var blending = 0
  var baseBlending = 0
  var lastFrame: ByteString = null
  var currentFrame: ByteString = null
  var images = Map.empty[Int, (Image, String)]
  var currentImage: Image = null

  var lastPatternSelect: PatternSelect = PatternSelect(0, 0, 0, 0, 0, 0)
  var redFactor = 1.0
  var greenFactor = 1.0
  var blueFactor = 1.0

  override def preStart = {

    def loadImage(filename: String, id: Int, name: String) = {
      val image = ImageIO.read(getClass.getResourceAsStream(filename))
      images = images + (id -> (Image(image.getHeight, image.getWidth, image), name))
    }

    val imageList = List("1000-Flames.jpg", "1001-Seahorse.jpg", "1002-Sparkle.png", "1003-Underwater.png", "1004-Blue Wave.jpg",
         "1005-Gold Bubbles.png", "1006-Grape Sunset.png", "1007-Purple Bubbles.png", "1008-Narrow Flames.jpg", "1009-Flower Flicker.jpg")
    imageList.foreach({ filename =>
      try {
        logger.info("Identifying image file: " + filename)
        val id = filename.split('-')(0).toInt
        val name = filename.split('-')(1).split('.')(0)
        PatternNames = PatternNames :+ id + "-" + name
      } catch {
        case x: Throwable => {
          logger.error("Image filenames are not in the correct format")
          throw new IllegalArgumentException
        }
      }
    })
    imageList.foreach({ filename =>
      try {
        logger.info("Loading image file: " + filename)
        val id = filename.split('-')(0).toInt
        val name = filename.split('-')(1).split('.')(0)
        loadImage("/data/" + filename, id, name)
      } catch {
        case x: Throwable => {
          logger.error("Image files are not in the correct format")
          throw new IllegalArgumentException
        }
      }
    })
    currentImage = images(LowerId)._1
  }

  def receive = {
    case CommandFailed(_: Connect) =>
      logger.warn("Failed to connect to OPC Server")
    case c @ Connected(remote, local) =>
      logger.info("Connected: " + self.path.name)
      val connection = sender
      connection ! Register(self)
      context become connected(connection)
    case select: PatternSelect =>
      logger.warn("Selecting pattern")
      selectImage(select)
    case HeartbeatRequest =>
      context.sender ! heartbeat
    case LedImageControllerConnect(connect) =>
      enableConnect.connect = connect
      if (enableConnect.connect) IO(Tcp) ! Connect(remote)
    case ConnectionTick =>
      if (enableConnect.connect) IO(Tcp) ! Connect(remote)
  }

  def pixelByteString(cursor: (Int, Int)): ByteString = {
    val volume = lastPatternSelect.intensity.toFloat / 255.0
    val pixel: Int = try {
      (currentImage.image.getRGB(cursor._1.max(0).min(currentImage.width - 1), cursor._2.max(0).min(currentImage.height - 1)))
    } catch {
      case _: Throwable => throw new IllegalArgumentException
    }
    val red = ((pixel >> 16 & 0xFF) * volume * redFactor).min(255.0).toByte
    val green = ((pixel >> 8 & 0xFF) * volume * greenFactor).min(255.0).toByte
    val blue = ((pixel & 0xFF) * volume * blueFactor).min(255.0).toByte
    if (apisHost) {
      ByteString(red, green, blue)
    } else if (reedsHost) {
      ByteString(blue, red, green)
    } else if (windflowersHost) {
      ByteString(red, green, blue)
    } else {
      ByteString(red, green, blue)
    }
  }

  def assembledPixelData(data: ByteString, offsets: List[Int], cursor: (Int, Int)): ByteString = {
    var newData = data
    var count = 0
    offsets.map({ x =>
      if (windflowersHost) {
        if (x % 2 == 0) {
          val position = pixelPositions(x)
          val pixelData = pixelByteString(((position._1 * horizontalPixelSpacing).toInt, (position._2 + cursor._2).toInt))
          newData = newData ++ pixelData ++ pixelData
          count = count + 2
          //newData = newData ++ pixelData
        }
      } else {
        val position = pixelPositions(x)
        val pixelData = pixelByteString(((position._1 * horizontalPixelSpacing).toInt, (position._2 + cursor._2).toInt))
        newData = newData ++ pixelData
      }
    })
    newData
    /*
    offsets match {
      case Nil => data
      case x :: Nil =>
        val position = pixelPositions(x)
        data ++ pixelByteString(((position._1 * horizontalPixelSpacing).toInt, (position._2 + cursor._2).toInt))
      case x :: y =>
        val position = pixelPositions(x)
        data ++ pixelByteString(((position._1 * horizontalPixelSpacing).toInt, (position._2 + cursor._2).toInt)) ++ assembledPixelData(data, y, cursor)
    }
    */
  }

  def selectImage(select: PatternSelect) = {
    if (currentImage != images(select.id)._1) {
      currentImage = images(select.id)._1
      globalCursor = (0, 0)
      lastFrame = ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, LedCountList, globalCursor)
      currentFrame = ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, LedCountList, globalCursor)
      redFactor = 1.0
      greenFactor = 1.0
      blueFactor = 1.0
      lastPatternSelect = PatternSelect(select.id, 128, 128, 128, select.speed, select.intensity)
    } else {
      redFactor = (select.red - 128.0) / 128.0 + 1.0
      greenFactor = (select.green - 128.0) / 128.0 + 1.0
      blueFactor = (select.blue - 128.0) / 128.0 + 1.0
      lastPatternSelect = PatternSelect(select.id, select.red, select.green, select.blue, select.speed, select.intensity)
    }
  }

  def heartbeat: Heartbeat = {
    val name = if (images.contains(lastPatternSelect.id)) images(lastPatternSelect.id)._2 else ""
    Heartbeat(new DateTime, 0, 0, lastPatternSelect.id, lastPatternSelect.red, lastPatternSelect.green, lastPatternSelect.blue,
      lastPatternSelect.speed, lastPatternSelect.intensity, 0, name)
  }

  def blendedFrames: ByteString = {
    ByteString(lastFrame.zip(currentFrame).map({ case (l, c) => {
      if (l == c) l
      else {
        val lB = if (l < 0) l + 255 else l
        val cB = if (c < 0) c + 255 else c
        val blendedByte = (lB + (cB - lB) * (1.0 - blending.toDouble / baseBlending.toDouble)).toByte
        blendedByte
      }
    } }).toArray)
  }

  def connected(connection: ActorRef): Receive = {
    case FrameTick =>
      if (blending <= 0) {
        blending = (512 - lastPatternSelect.speed * 2) / SpeedModifier
        baseBlending = blending
        if (direction == 1 && (globalCursor._2 + PixelHop >= currentImage.height - 1)) direction = -1
        else if (direction == -1 && (globalCursor._2 - PixelHop <= 0)) direction = 1
        globalCursor = (globalCursor._1, globalCursor._2 + direction * PixelHop)
        lastFrame =
          if (currentFrame == null) ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, LedCountList, globalCursor)
          else currentFrame
        currentFrame = ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, LedCountList, globalCursor)
      }
      blending = blending - 1
      connection ! Write(blendedFrames)
    case select: PatternSelect =>
      selectImage(select)
    case HeartbeatRequest =>
      context.sender ! heartbeat
    case LedImageControllerConnect(connect) =>
      logger.info("Receiving Connect Message")
      if (!connect) {
        logger.info("Shutting off Opc")
        enableConnect.connect = connect
        connection ! Close
      }
    case _: ConnectionClosed =>
      logger.info("Connection Closed")
      context become receive
  }

}
