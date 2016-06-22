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
import java.io.InputStream
import java.net.InetSocketAddress
import javax.imageio.ImageIO

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString
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
}

class LedImageController(remote: InetSocketAddress)  extends Actor with ActorLogging {

  import LedImageController._
  import Tcp._
  import context._

  val logger =  LoggerFactory.getLogger(getClass)

  val enableConnect = LedImageControllerConnect(false)
  val tickScheduler = context.system.scheduler.schedule (0 milliseconds, TickInterval, self, PwmTick)
  val connectScheduler = context.system.scheduler.schedule (0 milliseconds, ConnectionTickInterval, self, ConnectionTick)

  var cursor = (0, 0)
  var image: Image = null

  def horizontalPixelSpacing = image.width / LedColumns

  override def preStart = {
    //val tmpImage = ImageIO.read(new File("src/main/resources/data/flames.jpeg"))
    val inputStream: InputStream = getClass.getResourceAsStream("/data/underwater.png")
    val tmpImage = ImageIO.read(inputStream)
    image = Image(tmpImage.getHeight, tmpImage.getWidth, tmpImage)
    logger.info ("Height - " + image.height)
    logger.info ("Width - " + image.width)
  }

  def receive = {
    case CommandFailed(_: Connect) =>
      logger.warn("Failed to connect to OPC Server")
    case c @ Connected(remote, local) =>
      logger.info("Connected: " + self.path.name)
      val connection = sender
      connection ! Register(self)
      context become connected(connection)
    case LedImageControllerConnect(connect) =>
      enableConnect.connect = connect
    case ConnectionTick =>
      if (enableConnect.connect) IO(Tcp) ! Connect(remote)
  }

  def pixelByteString(cursor: (Int, Int)): ByteString = {
    val pixel: Int = image.image.getRGB(cursor._1,
      cursor._2 - (cursor._2 / image.height) * image.height)
    ByteString((pixel >> 16).toByte, (pixel >> 8).toByte, (pixel).toByte)
  }

  def assembledPixelData(data: ByteString, offsets: List[Int]): ByteString = {
    offsets match {
      case Nil => data
      case x :: Nil =>
        data ++ pixelByteString((cursor._1 + x / LedRows * horizontalPixelSpacing, cursor._2 + x % LedColumns * horizontalPixelSpacing))
      case x :: y =>
        logger.info("data.length = " + data.length + ", x = " + x + ", y.length = " + y.length)
        data ++ pixelByteString((cursor._1 + x / LedRows * horizontalPixelSpacing, cursor._2 + x % LedColumns * horizontalPixelSpacing)) ++ assembledPixelData(data, y)
    }
  }

  def connected(connection: ActorRef): Receive = {
    case PwmTick =>
      if (cursor._2 >= image.height) cursor = (cursor._1, 1)
      else cursor = (cursor._1, cursor._2 + 2)
      val bytes = ByteString(0, 0, (NumBytes >> 8).toByte, NumBytes.toByte) ++ assembledPixelData(ByteString.empty, (1 to LedCount).toList)
      connection ! Write(bytes)
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
