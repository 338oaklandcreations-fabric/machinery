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

import akka.util.Timeout
import org.slf4j.{Logger, LoggerFactory}
import spray.routing._
import spray.routing.authentication._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Properties.envOrElse

object UserAuthentication {
  case class AuthenticationRejection(reason: String) extends Rejection
  case class AuthenticationRequest(username: String, password: String)
  case class SessionAuthenticationRequest(sessionId: String, username: String)
  case class Authenticated(token: String)
  case class AuthenticationRejected(reason: String)
  case class UserRegistration(email: String, firstName: String, lastName: String, password: String)
  case class LogoutRequest(sessionId: String, username: String)}

trait UserAuthentication {

  import UserAuthentication._

  implicit val defaultTimeout = Timeout(3 seconds)

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def authentications = {
    val userPWD = envOrElse("FABRIC_USER_PASSWORDS", "mauricio,2015")
    userPWD.split(";").map(_.split(",")).map({ x => (x(0), x(1)) }).toMap
  }

  def authenticateSessionId(sessionId: String, username: String)(implicit ec: ExecutionContext): ContextAuthenticator[Authenticated] = {
    ctx =>
    {
      logger.info("Authenticating Session: " + sessionId + ":" + username)
      doSessionAuth(sessionId, username)
    }
  }

  private def doSessionAuth(sessionId: String, email: String)(implicit ec: ExecutionContext): Future[Authentication[Authenticated]] = {
    Future {
      val result = {
        if (sessionId != "") Authenticated(sessionId)
        else AuthenticationRejection("Unknown Email")
      }
      Either.cond(result match {
        case Authenticated(token) => true
        case _ => false
      },
        result match { case x: Authenticated => x },
        result match { case AuthenticationRejection(message) => AuthenticationRejection(message) }
      )
    }
  }
}
