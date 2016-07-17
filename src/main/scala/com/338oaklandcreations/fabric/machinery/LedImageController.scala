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
import scala.sys.process.Process

object LedImageController {
  def props(remote: InetSocketAddress) = Props(classOf[LedImageController], remote)

  case object FrameTick
  case object ConnectionTick

  case class LedImageControllerConnect(var connect: Boolean)
  case class Image(height: Int, width: Int, image: BufferedImage)
  case class Point(point: List[Double])

  val ConnectionTickInterval = 5 seconds
  val TickInterval = 16 milliseconds

  val LedRows = 10
  val LedColumns = 72
  val SpeedModifier = 1
  val PixelHop = 20

  val isArm = {
    val hosttype = Process(Seq("bash", "-c", "echo $HOSTTYPE")).!!.replaceAll("\n", "")
    hosttype == "arm"
  }

  val LedCount = {
    if (isArm) LedColumns
    else LedColumns
  }

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

  val enableConnect = LedImageControllerConnect(false)
  val tickScheduler = context.system.scheduler.schedule (0 milliseconds, TickInterval, self, FrameTick)
  val connectScheduler = context.system.scheduler.schedule (0 milliseconds, ConnectionTickInterval, self, ConnectionTick)

  var globalCursor = (0, 0)
  var direction = 1
  var blending = 0
  var baseBlending = 0
  var lastFrame: ByteString = null
  var currentFrame: ByteString = null
  var images = Map.empty[Int, (Image, String)]
  var currentImage: Image = null

  var lastPatternSelect: PatternSelect = PatternSelect(0, 0, 0, 0, 0, 0)

  def horizontalPixelSpacing = currentImage.width / (LedColumns + 1)

  override def preStart = {

    def loadImage(filename: String, id: Int, name: String) = {
      val image = ImageIO.read(getClass.getResourceAsStream(filename))
      images = images + (id -> (Image(image.getHeight, image.getWidth, image), name))
      PatternNames = PatternNames :+ id + "-" + name
    }

    List("1000-Flames.jpg", "1001-Seahorse.jpg", "1002-Sparkle.png", "1003-Underwater.png").foreach({ filename =>
      try {
        logger.info("Found image file: " + filename)
        val id = filename.split('-')(0).toInt
        val name = filename.split('-')(1).split('.')(0)
        loadImage("/data/" + filename, id, name)
        currentImage = images(LowerId)._1
      } catch {
        case _: Throwable => {
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
    val pixel: Int = try {
      currentImage.image.getRGB(cursor._1, cursor._2)
    } catch {
      case _: Throwable => throw new IllegalArgumentException
    }
    if (isArm) ByteString((pixel).toByte, (pixel >> 16).toByte, (pixel >> 8).toByte)
    else ByteString((pixel >> 16).toByte, (pixel >> 8).toByte, (pixel).toByte)
  }

  def assembledPixelData(data: ByteString, offsets: List[Int], cursor: (Int, Int)): ByteString = {
    offsets match {
      case Nil => data
      case x :: Nil =>
        data ++ pixelByteString((cursor._1 + x * horizontalPixelSpacing, cursor._2))
      case x :: y =>
        data ++ pixelByteString((cursor._1 + x * horizontalPixelSpacing, cursor._2)) ++ assembledPixelData(data, y, cursor)
    }
  }

  def selectImage(select: PatternSelect) = {
    if (currentImage != images(select.id)._1) {
      currentImage = images(select.id)._1
      globalCursor = (0, 0)
      lastFrame = ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, (1 to LedCount).toList, globalCursor)
      currentFrame = ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, (1 to LedCount).toList, globalCursor)
    }
    lastPatternSelect = PatternSelect(select.id, select.red, select.green, select.blue, select.speed, select.intensity)
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
        blending = (255 - lastPatternSelect.speed) / SpeedModifier
        baseBlending = blending
        if (direction == 1 && (globalCursor._2 + PixelHop >= currentImage.height - 1)) direction = -1
        else if (direction == -1 && (globalCursor._2 - PixelHop <= 0)) direction = 1
        globalCursor = (globalCursor._1, globalCursor._2 + direction * PixelHop)
        lastFrame =
          if (currentFrame == null) ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, (1 to LedCount).toList, globalCursor)
          else currentFrame
        currentFrame = ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, (1 to LedCount).toList, globalCursor)
      }
      blending = blending - 1
      connection ! Write(blendedFrames)
    case select: PatternSelect =>
      selectImage(select)
    case HeartbeatRequest =>
      context.sender ! heartbeat
    case LedImageControllerConnect(connect) =>
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
