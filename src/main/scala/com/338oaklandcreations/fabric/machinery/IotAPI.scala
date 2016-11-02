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

import akka.actor.{Actor, ActorLogging}
import akka.util.Timeout
import com._338oaklandcreations.fabric.machinery.IotAPI.Message
import com.pubnub.api.callbacks.PNCallback
import com.pubnub.api.models.consumer.{PNPublishResult, PNStatus}
import com.pubnub.api.{PNConfiguration, PubNub}
import org.slf4j.LoggerFactory

import scala.util.Properties._
import scala.concurrent.duration._

object IotAPI {

  case class Message(channel: String, payload: String)

  val SunTimingChannel = "sunTiming"
  val PatternUpdateChannel = "patternUpdate"

}

class IotAPI extends Actor with ActorLogging {

  implicit val defaultTimeout = Timeout(3 seconds)

  val logger =  LoggerFactory.getLogger(getClass)

  val pnConfiguration = new PNConfiguration()
  pnConfiguration.setSubscribeKey(envOrElse("PN_SUBSCRIBE_KEY", "sub-c-27d02298-9f02-11e6-aff8-0619f8945a4f"))
  pnConfiguration.setPublishKey(envOrElse("PN_PUBLISH_KEY", "pub-c-3148481f-1fb2-4e8b-adae-97fbd1b90964"))
  pnConfiguration.setSecure(true)

  val pubnub = new PubNub(pnConfiguration)

  def receive = {
    case message: Message => {
      pubnub.publish()
        .message(message.payload)
        .channel(message.channel)
        .shouldStore(true)
        .usePOST(true)
        .async(new PNCallback[PNPublishResult]() {
          def onResponse(result: PNPublishResult, status: PNStatus) = {
            logger.debug("Acknowledged")
          }
        })
    }
    case x => logger.info ("Unknown Command: " + x.toString())
  }
}
