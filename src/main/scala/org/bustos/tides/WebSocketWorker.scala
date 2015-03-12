package org.bustos.tides

import akka.actor.{ ActorSystem, Actor, Props, ActorLogging, ActorRef, ActorRefFactory }
import akka.pattern.ask
import akka.util.{ Timeout }
import scala.concurrent._
import scala.concurrent.duration._
import spray.util._
import spray.http._
import spray.routing.HttpServiceActor
import spray.json._
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket._
import spray.can.websocket.frame.{ BinaryFrame, TextFrame }
import spray.routing._
import Directives._
import org.slf4j.{ Logger, LoggerFactory }

object WebSocketWorker {
  case class Push(msg: String)

  def props(serverConnection: ActorRef) = Props(classOf[WebSocketWorker], serverConnection)
}

class WebSocketWorker(val serverConnection: ActorRef) extends HttpServiceActor with WebSocketServerWorker with TidesRoutes {

  import WebSocketWorker._

  override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic

  val controller = context.actorOf(Props[TidesController], "controller")

  def businessLogic: Receive = {
    // just bounce frames back for Autobahn testsuite
    case x @ (_: BinaryFrame | _: TextFrame) =>
      sender() ! x

    case Push(msg) =>
      send(TextFrame(msg))

    case x: FrameCommandFailed =>
      log.error("frame command failed", x)

    case x: HttpRequest => println("BL: " + x) // do something
  }

  def businessLogicNoUpgrade: Receive = {

    implicit val refFactory: ActorRefFactory = context

    runRoute(tidesRoutes)
  }
}
