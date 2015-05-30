package com._338oaklandcreations.fabric.machinery

import akka.actor.{ ActorSystem, Actor, Props, ActorLogging, ActorRef, ActorRefFactory }
import akka.io.IO
import spray.http._
import spray.can.Http
import spray.can.websocket.frame.{ BinaryFrame, TextFrame }
import scala.collection.mutable.HashSet
import org.slf4j.LoggerFactory

object WebSocketServer {
  def props() = Props(classOf[WebSocketServer])
}
  
class WebSocketServer extends Actor with ActorLogging {
  
  val logger = LoggerFactory.getLogger(getClass)

  val controller = context.actorOf(Props[MachineryController], "controller")
 
  def receive = {
    case Http.Connected(remoteAddress, localAddress) =>
      val serverConnection = sender()
      val conn = context.actorOf(WebSocketWorker.props(serverConnection, controller))
      serverConnection ! Http.Register(conn)
  }
  
}
