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
import java.io.PrintWriter
import java.net.{InetSocketAddress, Socket}
import javax.imageio.ImageIO

import akka.actor.{ActorRef, Actor, ActorLogging, Props}
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
  val FrameCountInterval = 100
  val TickInterval = {
    if (windflowersHost) 100 milliseconds
    else 12 milliseconds
  }

  val SpeedModifier = 1
  val PixelHop = {
    if (windflowersHost) 10
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

  var socket: Socket = null
  var socketWrite: PrintWriter = null

  val layout: LedPlacement = {
    if (apisHost) ApisPlacement
    else if (reedsHost) ReedsPlacement
    else if (windflowersHost) WindflowersPlacement
    else WindflowersPlacement
  }

  val pixelPositions: List[(Double, Double)] = layout.positions

  var horizontalPixelSpacing = 100

  var globalCursor = (0, 0)
  var direction = 1
  var blending = 0
  var baseBlending = 0
  var lastFrame: ByteString = null
  var currentFrame: ByteString = null
  var images = Map.empty[Int, (Image, String)]
  var currentImage: Image = null
  var frameCount = 0L
  var frameBuildTimeMicroSeconds = 0.0
  var frameCountTimeMicroSeconds = 0.0
  var frameSocketTimeMicroSeconds = 0.0
  var volume = 1.0;

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

  def pixelByteString(cursor: (Int, Int), blendingFactor: Double, pixelIndex: Int): ByteString = {
    val pixel: Int = try {
      if (currentImage == null) 0
      else (currentImage.image.getRGB(cursor._1.max(0).min(currentImage.width - 1), cursor._2.max(0).min(currentImage.height - 1)))
    } catch {
      case _: Throwable => throw new IllegalArgumentException
    }

    val lastRedByte = if (lastFrame == null) 0 else lastFrame(pixelIndex * 3 + 4)
    val lastRed = if (lastRedByte < 0) lastRedByte + 255 else lastRedByte
    val newRed = ((pixel >> 16 & 0xFF) * volume * redFactor).min(255.0)
    val blendedRed = (lastRed + (newRed - lastRed) * blendingFactor).toByte

    val lastGreenByte = if (lastFrame == null) 0 else lastFrame(pixelIndex * 3 + 5)
    val lastGreen = if (lastGreenByte < 0) lastGreenByte + 255 else lastGreenByte
    val newGreen = ((pixel >> 8 & 0xFF) * volume * greenFactor).min(255.0)
    val blendedGreen = (lastGreen + (newGreen - lastGreen) * blendingFactor).toByte

    val lastBlueByte = if (lastFrame == null) 0 else lastFrame(pixelIndex * 3 + 6)
    val lastBlue = if (lastBlueByte < 0) lastBlueByte + 255 else lastBlueByte
    val newBlue = ((pixel & 0xFF) * volume * blueFactor).min(255.0)
    val blendedBlue = (lastBlue + (newBlue - lastBlue) * blendingFactor).toByte

    if (apisHost) {
      ByteString(blendedRed, blendedGreen, blendedBlue)
    } else if (reedsHost) {
      ByteString(blendedBlue, blendedRed, blendedGreen)
    } else if (windflowersHost) {
      ByteString(blendedRed, blendedGreen, blendedBlue)
    } else {
      ByteString(blendedRed, blendedGreen, blendedBlue)
    }
  }

  def assembledPixelData(data: ByteString, offsets: List[Int], cursor: (Int, Int)): ByteString = {
    var newData = Array.fill[Byte](offsets.length * 3)(0)
    var count = 0
    val startus = System.nanoTime()
    val blendingFactor = (1.0 - blending.toDouble / baseBlending.toDouble)
    offsets.foreach({ x =>
      if (windflowersHost) {
        if (x % 2 == 0) {
          val position = pixelPositions(x)
          val pixelData = pixelByteString(((position._1 * horizontalPixelSpacing).toInt, (position._2 + cursor._2).toInt), blendingFactor, x)
          newData = newData ++ pixelData ++ pixelData
          count = count + 2
          //newData = newData ++ pixelData
        }
      } else {
        val position = pixelPositions(x)
        val pixelData = pixelByteString(((position._1 * horizontalPixelSpacing).toInt, (position._2 + cursor._2).toInt), blendingFactor, x)
        newData(x * 3) = pixelData(0)
        newData(x * 3 + 1) = pixelData(1)
        newData(x * 3 + 2) = pixelData(2)
        //newData = newData ++ pixelData
      }
    })
    val updatedData = data ++ ByteString(newData)
    frameBuildTimeMicroSeconds += (System.nanoTime() - startus).toDouble / 1000000.0
    updatedData
  }

  def selectImage(select: PatternSelect) = {
    frameCount = 0
    if (currentImage != images(select.id)._1) {
      currentImage = images(select.id)._1
      horizontalPixelSpacing = currentImage.width / layout.layoutWidth
      globalCursor = (0, 0)
      lastFrame = ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, LedCountList, globalCursor)
      currentFrame = ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, LedCountList, globalCursor)
      redFactor = 1.0
      greenFactor = 1.0
      blueFactor = 1.0
      lastPatternSelect = PatternSelect(select.id, 128, 128, 128, select.speed, select.intensity)
      volume = lastPatternSelect.intensity.toFloat / 255.0
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

  def connected(connection: ActorRef): Receive = {
//  def receive = {
    case FrameTick =>
      if (enableConnect.connect) {
        val startus = System.nanoTime()
      frameCount += 1
      if (blending <= 0) {
        blending = (512 - lastPatternSelect.speed * 2) / SpeedModifier
        baseBlending = blending
        if (currentImage == null || (direction == 1 && (globalCursor._2 + PixelHop >= currentImage.height - 1))) direction = -1
        else if (direction == -1 && (globalCursor._2 - PixelHop <= 0)) direction = 1
        globalCursor = (globalCursor._1, globalCursor._2 + direction * PixelHop)
        lastFrame =
          if (currentFrame == null) ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, LedCountList, globalCursor)
          else currentFrame
        //currentFrame = ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, LedCountList, globalCursor)
      }
      currentFrame = ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, LedCountList, globalCursor)
      blending -= 1

        frameCountTimeMicroSeconds += (System.nanoTime() - startus).toDouble / 1000000.0
        val startsocket = System.nanoTime()
        //val data: Array[Byte] = currentFrame.toArray
        //data.foreach({ byte => socketWrite.print(byte.toChar)})
        //socketWrite.flush
        connection ! Write(currentFrame)
        frameSocketTimeMicroSeconds += (System.nanoTime() - startsocket).toDouble / 1000000.0

      if (frameCount % FrameCountInterval == 0) {
        logger.info(FrameCountInterval + " Frames at " + frameCountTimeMicroSeconds / FrameCountInterval+ " ms / frame")
        logger.info("Frame build time " + frameBuildTimeMicroSeconds / FrameCountInterval + " ms / frame")
        logger.info("Frame comm time " + frameSocketTimeMicroSeconds / FrameCountInterval + " ms / frame")
        frameCountTimeMicroSeconds = 0
        frameBuildTimeMicroSeconds = 0
        frameSocketTimeMicroSeconds = 0
      }}
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
      /*
    case LedImageControllerConnect(connect) =>
      enableConnect.connect = connect
      logger.info("Receiving Connect Message")
      if (connect) {
        if (socket == null) {
          socket = new Socket(remote.getHostName, remote.getPort)
          socketWrite = new PrintWriter(socket.getOutputStream(), true)
        }
      } else {
        logger.info("Shutting off Opc")
        if (socket != null) {
          socket.close
          socket = null
          socketWrite = null
        }
      }
      */
    case _: ConnectionClosed =>
      logger.info("Connection Closed")
      context become receive
  }

}
