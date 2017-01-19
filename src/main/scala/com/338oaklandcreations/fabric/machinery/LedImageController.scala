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

import java.awt.image.DataBufferByte
import java.net.InetSocketAddress
import javax.imageio.ImageIO

import akka.actor._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import sun.awt.image.ByteInterleavedRaster

import scala.concurrent.duration._

object LedImageController extends HostActor with HostAware {

  def props(remote: InetSocketAddress) = Props(classOf[LedImageController], remote)

  case object FrameTick
  case object ConnectionTick

  case class LedImageControllerConnect(var connect: Boolean)
  //case class Image(height: Int, width: Int, image: BufferedImage)
  case class Image(height: Int, width: Int, pixelStride: Int, image: Array[Byte])
  case class Point(point: List[Double])

  val ConnectionTickInterval = 5 seconds
  val FrameRate = {
    if (reedsHost || fabric338Host) 60
    else if (windflowersHost) 30
    else 30
  }
  val FrameDisplayRate = FrameRate * 250
  val TickInterval = ((1.0 / FrameRate) * 1000) milliseconds

  val SpeedModifier = 1
  val PixelHop = {
    5
  }

  val LedCount = {
    if (apisHost) 101
    else if (reedsHost || fabric338Host) ReedsPlacement.positions.length
    else if (windflowersHost) WindflowersPlacement.positions.length
    else WindflowersPlacement.positions.length
  }

  val Layout: LedPlacement = {
    if (apisHost) ApisPlacement
    else if (reedsHost || fabric338Host) ReedsPlacement
    else if (windflowersHost) WindflowersPlacement
    else WindflowersPlacement
  }

  val LedCountList = (0 to LedCount - 1).toList.map(_ * 3 + 4)
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

  val pixelPositions: List[(Double, Double)] = Layout.positions

  var horizontalPixelSpacing = 100

  var globalCursor = (0, 0)
  var direction = 1
  var blending = 0
  var baseBlending = 0
  val lastFrame: Array[Byte] = Array.fill[Byte](LedCountList.length * 3 + 4)(0)
  val currentFrame: Array[Byte] = Array.fill[Byte](LedCountList.length * 3 + 4)(0)
  var images = Map.empty[Int, (Image, String)]
  var currentImage: Image = null
  var frameCount = 0L
  var frameBuildTimeMilliSeconds = 0.0
  var frameCountTimeMilliSeconds = 0.0
  var volume = 1.0

  var lastPatternSelect: PatternSelect = PatternSelect(0, 0, 0, 0, 0, 0)
  var redFactor = 1.0
  var greenFactor = 1.0
  var blueFactor = 1.0

  override def preStart = {

    def loadImage(filename: String, id: Int, name: String) = {
      val image = ImageIO.read(getClass.getResourceAsStream(filename))
      val imageBytes = image.getRaster.getDataBuffer.asInstanceOf[DataBufferByte].getData
      images = images + (id -> (Image(image.getHeight, image.getWidth, image.getRaster.asInstanceOf[ByteInterleavedRaster].getPixelStride, imageBytes), name))
    }

    val imageList = List(
      "1000-Flames.jpg", "1001-Seahorse.jpg",
      "1002-Sparkle.png", "1003-Underwater.png",
      "1004-Blue Wave.jpg", "1005-Gold Bubbles.png",
      "1006-Grape Sunset.png", "1007-Purple Bubbles.png",
      "1008-Narrow Flames.jpg", "1009-Flower Flicker.jpg"
    )
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

  def assembledPixelData(offsets: List[Int], cursor: (Int, Int), frame: Array[Byte]) = {
    frame(2) = (NumBytes >> 8).toByte
    frame(3) = NumBytes.toByte
    val startus = System.nanoTime
    if (currentImage == null || lastFrame == null) {
      offsets.foreach({ x =>
        frame(x) = 0
        frame(x + 1) = 0
        frame(x + 2) = 0
      })
    } else {
      val blendingFactor = (1.0 - blending.toDouble / baseBlending.toDouble)
      offsets.foreach({ x =>
        val position = pixelPositions((x - 4) / 3)
        /*
        val pixel: Int = try {
          currentImage.image.getRGB(
            (position._1 * horizontalPixelSpacing).toInt.max(0).min(currentImage.width - 1),
            (position._2 + cursor._2).toInt.max(0).min(currentImage.height - 1))
        } catch {
          case _: Throwable => throw new IllegalArgumentException
        }
        */
        val byteIndex = {
          val index = (position._1 * currentImage.pixelStride * horizontalPixelSpacing + (position._2 + cursor._2) * currentImage.pixelStride * currentImage.width).toInt.
            max(0).min(currentImage.image.length - 1)
          if (index >= currentImage.image.length - currentImage.pixelStride) {
            currentImage.image.length - currentImage.pixelStride
          } else {
            if (currentImage.pixelStride == 4) index + 1
            else index
          }
        }
        val lastRedByte = lastFrame(x)
        val lastRed = if (lastRedByte < 0) lastRedByte + 255 else lastRedByte
        //val newRed = ((pixel >> 16 & 0xFF) * redFactor).min(255.0)
        val newRed = (currentImage.image(byteIndex + 2) * redFactor).min(255.0)
        val blendedRed = (lastRed + (newRed - lastRed) * blendingFactor).toByte

        val lastGreenByte = lastFrame(x + 1)
        val lastGreen = if (lastGreenByte < 0) lastGreenByte + 255 else lastGreenByte
        //val newGreen = ((pixel >> 8 & 0xFF) * greenFactor).min(255.0)
        val newGreen = (currentImage.image(byteIndex + 1) * greenFactor).min(255.0)
        val blendedGreen = (lastGreen + (newGreen - lastGreen) * blendingFactor).toByte

        val lastBlueByte = lastFrame(x + 2)
        val lastBlue = if (lastBlueByte < 0) lastBlueByte + 255 else lastBlueByte
        //val newBlue = ((pixel & 0xFF) * blueFactor).min(255.0)
        val newBlue = (currentImage.image(byteIndex) * blueFactor).min(255.0)
        val blendedBlue = (lastBlue + (newBlue - lastBlue) * blendingFactor).toByte

        if (apisHost || windflowersHost || fabric338Host) {
          frame(x) = blendedRed
          frame(x + 1) = blendedGreen
          frame(x + 2) = blendedBlue
        } else if (reedsHost) {
          frame(x) = blendedBlue
          frame(x + 1) = blendedRed
          frame(x + 2) = blendedGreen
        } else {
          frame(x) = blendedRed
          frame(x + 1) = blendedGreen
          frame(x + 2) = blendedBlue
        }
      })
    }
    frameBuildTimeMilliSeconds += (System.nanoTime - startus).toDouble / 1000000.0
  }

  def selectImage(select: PatternSelect) = {
    frameCount = 0
    if (currentImage != images(select.id)._1) {
      logger.warn("PatternSelect: " + select.id)
      currentImage = images(select.id)._1
      horizontalPixelSpacing = currentImage.width / Layout.layoutWidth
      globalCursor = (0, 0)
      assembledPixelData(LedCountList, globalCursor, lastFrame)
      assembledPixelData(LedCountList, globalCursor, currentFrame)
      volume = lastPatternSelect.intensity.toFloat / 255.0
      redFactor = lastPatternSelect.intensity.toFloat / 255.0
      greenFactor = lastPatternSelect.intensity.toFloat / 255.0
      blueFactor = lastPatternSelect.intensity.toFloat / 255.0
      lastPatternSelect = PatternSelect(select.id, 128, 128, 128, select.speed, select.intensity)
    } else {
      volume = lastPatternSelect.intensity.toFloat / 255.0
      redFactor = (((select.red - 128.0) / 128.0 + 1.0) * volume).min(255.0)
      greenFactor = (((select.green - 128.0) / 128.0 + 1.0) * volume).min(255.0)
      blueFactor = (((select.blue - 128.0) / 128.0 + 1.0) * volume).min(255.0)
      lastPatternSelect = PatternSelect(select.id, select.red, select.green, select.blue, select.speed, select.intensity)
    }
  }

  def heartbeat: Heartbeat = {
    val name = if (images.contains(lastPatternSelect.id)) images(lastPatternSelect.id)._2 else ""
    Heartbeat(new DateTime, 0, 0, lastPatternSelect.id, lastPatternSelect.red, lastPatternSelect.green, lastPatternSelect.blue,
      lastPatternSelect.speed, lastPatternSelect.intensity, 0, name)
  }

  def connected(connection: ActorRef): Receive = {
    case FrameTick =>
      if (enableConnect.connect) {
        frameCount += 1
        if (blending <= 0) {
          assembledPixelData(LedCountList, globalCursor, lastFrame)
          globalCursor = (globalCursor._1, globalCursor._2 + direction * PixelHop)
          blending = (256 - lastPatternSelect.speed) / SpeedModifier
          baseBlending = blending
          if (currentImage == null || (direction == 1 && (globalCursor._2 + PixelHop >= currentImage.height - 1))) direction = -1
          else if (direction == -1 && (globalCursor._2 - PixelHop <= 0)) direction = 1
        }
        assembledPixelData(LedCountList, globalCursor, currentFrame)
        blending -= 1

        connection ! Write(ByteString(currentFrame))
        if (frameCount % FrameDisplayRate == 0) {
          logger.warn(FrameDisplayRate + " Frames at " + "%1.3f".format((System.nanoTime / 1000000.0 - frameCountTimeMilliSeconds) / FrameDisplayRate) + " ms / frame")
          logger.warn("Frame build time " + "%1.3f".format(frameBuildTimeMilliSeconds / FrameDisplayRate) + " ms / frame")
          frameCount = 0
          frameCountTimeMilliSeconds = System.nanoTime / 1000000.0
          frameBuildTimeMilliSeconds = 0
        }
      }
    case select: PatternSelect =>
      selectImage(select)
    case HeartbeatRequest =>
      logger.warn (heartbeat.toString)
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
