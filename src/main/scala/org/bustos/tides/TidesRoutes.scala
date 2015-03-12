package org.bustos.tides

import spray.can.Http
import spray.can.server.UHttp
import spray.routing._
import spray.json._
import spray.http._
import MediaTypes._
import DefaultJsonProtocol._
import akka.actor.{ ActorRef }
import org.slf4j.{ Logger, LoggerFactory }

trait TidesRoutes extends HttpService {

  import TidesController._
  import java.net.InetSocketAddress

  val logger = LoggerFactory.getLogger(getClass)

  val controller: ActorRef

  val tidesRoutes = {
    getFromResourceDirectory("webapp") ~
      path("register") {
        parameters('id, 'address, 'port.as[Int]) { (id, address, port) =>
          controller ! NodeConnect(new InetSocketAddress(address, port))
          complete("")
        }
      } ~
      path("years") {
        respondWithMediaType(`application/json`) {
          complete("")
          //complete(realityballData.years.toJson.toString)
        }
      } ~
      path("graph") {
        parameters('team.?, 'player.?, 'year.?) { (team, player, year) =>
          val teamStr = team.getOrElse("")
          val playerStr = player.getOrElse("")
          val yearStr = year.getOrElse("")
          respondWithMediaType(`text/html`) {
            complete("")
            /*            if (!teamStr.isEmpty) {
              complete(html.graph.render("", "").toString)
            } else if (!playerStr.isEmpty && !yearStr.isEmpty) {
              complete(html.graph.render(playerStr, yearStr).toString)
            } else {
              complete(html.graph.render("", "").toString)
            }
            */
          }
        }
      }
  }
}
