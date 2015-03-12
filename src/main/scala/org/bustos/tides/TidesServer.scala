package org.bustos.tides

import akka.actor.{ ActorSystem, Actor, Props, ActorLogging, ActorRef, ActorRefFactory }
import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import scala.concurrent.duration._

object TidesServer extends App with MySslConfiguration {

  def doMain() {

    implicit val system = ActorSystem()
    import system.dispatcher

    implicit val timeout = Timeout(DurationInt(5).seconds)

    val server = system.actorOf(WebSocketServer.props(), "websocket")

    if (args.length > 0) IO(UHttp) ? Http.Bind(server, "0.0.0.0", args(0).toInt)
    //else IO(UHttp) ? Http.Bind(server, "localhost", 8100)
    else IO(UHttp) ? Http.Bind(server, "10.0.1.8", 8100)
  }

  doMain()
}
