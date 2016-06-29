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

object LedImageController {
  def props(remote: InetSocketAddress) = Props(classOf[LedImageController], remote)

  case object PwmTick
  case object ConnectionTick

  case class LedImageControllerConnect(var connect: Boolean)
  case class Image(height: Int, width: Int, image: BufferedImage)
  case class Point(point: List[Double])

  val ConnectionTickInterval = 5 seconds
  val TickInterval = 5 milliseconds

  val LedRows = 40
  val LedColumns = 80
  val LedCount = LedRows * LedColumns
  val NumBytes = LedCount * 3

  val LowerId = 1000
  val UnderwaterId = 1000
  val UnderwaterName = "Underwater"
  val FireId = 1001
  val FireName = "Flames"
}

class LedImageController(remote: InetSocketAddress)  extends Actor with ActorLogging {

  import LedController.{Heartbeat, HeartbeatRequest, PatternSelect}
  import LedImageController._
  import Tcp._
  import context._

  val logger =  LoggerFactory.getLogger(getClass)

  val enableConnect = LedImageControllerConnect(false)
  val tickScheduler = context.system.scheduler.schedule (0 milliseconds, TickInterval, self, PwmTick)
  val connectScheduler = context.system.scheduler.schedule (0 milliseconds, ConnectionTickInterval, self, ConnectionTick)

  var cursor = (0, 0)
  var images = Map.empty[Int, (Image, String)]
  var currentImage: Image = null

  var lastPatternSelect: PatternSelect = PatternSelect(0, 0, 0, 0, 0, 0)

  def horizontalPixelSpacing = currentImage.width / LedColumns

  override def preStart = {

    def loadImage(filename: String, id: Int, name: String) = {
      val image = ImageIO.read(getClass.getResourceAsStream(filename))
      images = images + (id -> (Image(image.getHeight, image.getWidth, image), name))
    }

    loadImage("/data/underwater.png", UnderwaterId, UnderwaterName)
    loadImage("/data/flames.jpeg", FireId, FireName)

    currentImage = images(UnderwaterId)._1
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
    val pixel: Int = currentImage.image.getRGB(cursor._1,
      cursor._2 - (cursor._2 / currentImage.height) * currentImage.height)
    ByteString((pixel >> 16).toByte, (pixel >> 8).toByte, (pixel).toByte)
  }

  def assembledPixelData(data: ByteString, offsets: List[Int]): ByteString = {
    offsets match {
      case Nil => data
      case x :: Nil =>
        data ++ pixelByteString((cursor._1 + x / LedRows * horizontalPixelSpacing, cursor._2 + x % LedColumns * horizontalPixelSpacing))
      case x :: y =>
        //logger.info("data.length = " + data.length + ", x = " + x + ", y.length = " + y.length)
        data ++ pixelByteString((cursor._1 + x / LedRows * horizontalPixelSpacing, cursor._2 + x % LedColumns * horizontalPixelSpacing)) ++ assembledPixelData(data, y)
    }
  }

  def selectImage(select: PatternSelect) = {
    currentImage = images(select.id)._1
    lastPatternSelect = PatternSelect(select.id, select.red, select.green, select.blue, select.speed, select.intensity)
  }

  def heartbeat: Heartbeat = {
    val name = if (images.contains(lastPatternSelect.id)) images(lastPatternSelect.id)._2 else ""
    Heartbeat(new DateTime, 0, 0, lastPatternSelect.id, lastPatternSelect.red, lastPatternSelect.green, lastPatternSelect.blue,
      lastPatternSelect.speed, lastPatternSelect.intensity, 0, name)
  }

  def connected(connection: ActorRef): Receive = {
    case PwmTick =>
      if (cursor._2 >= currentImage.height) cursor = (cursor._1, 1)
      else cursor = (cursor._1, cursor._2 + 2)
      val bytes = ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, (1 to LedCount).toList)
      connection ! Write(bytes)
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
