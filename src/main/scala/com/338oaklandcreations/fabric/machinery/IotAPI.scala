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
import com.pubnub.api.callbacks.{PNCallback, SubscribeCallback}
import com.pubnub.api.enums._
import com.pubnub.api.models.consumer._
import com.pubnub.api.models.consumer.history.PNHistoryResult
import com.pubnub.api.models.consumer.pubsub._
import com.pubnub.api.{PNConfiguration, PubNub}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.Properties._

object IotAPI {

  case class ExternalMessage(channel: String, timeStamp: DateTime, payload: String)
  case class ExternalMessages(messages: List[ExternalMessage])

  val SunTimingChannel = "sunTiming"
  val PatternUpdateChannel = "patternUpdate"

}

class IotAPI extends Actor with ActorLogging {

  implicit val defaultTimeout = Timeout(3 seconds)

  import IotAPI._

  val logger =  LoggerFactory.getLogger(getClass)

  val pnConfiguration = new PNConfiguration()
  pnConfiguration.setSubscribeKey(envOrElse("PN_SUBSCRIBE_KEY", "sub-c-27d02298-9f02-11e6-aff8-0619f8945a4f"))
  pnConfiguration.setPublishKey(envOrElse("PN_PUBLISH_KEY", "pub-c-3148481f-1fb2-4e8b-adae-97fbd1b90964"))
  pnConfiguration.setSecure(true)

  val pubnub = new PubNub(pnConfiguration)

  pubnub.addListener(new SubscribeCallback() {

    def status(pubnub: PubNub, status: PNStatus) = {
      // the status object returned is always related to subscribe but could contain
      // information about subscribe, heartbeat, or errors
      // use the operationType to switch on different options
      status.getOperation match {
        // let's combine unsubscribe and subscribe handling for ease of use
        case PNOperationType.PNSubscribeOperation =>
        case PNOperationType.PNUnsubscribeOperation => {
          // note: subscribe statuses never have traditional
          // errors, they just have categories to represent the
          // different issues or successes that occur as part of subscribe

          status.getCategory match {
            case PNStatusCategory.PNConnectedCategory =>
            // this is expected for a subscribe, this means there is no error or issue whatsoever
            case PNStatusCategory.PNReconnectedCategory =>
            // this usually occurs if subscribe temporarily fails but reconnects. This means
            // there was an error but there is no longer any issue
            case PNStatusCategory.PNDisconnectedCategory =>
            // this is the expected category for an unsubscribe. This means there
            // was no error in unsubscribing from everythin
            case PNStatusCategory.PNUnexpectedDisconnectCategory =>
            // this is usually an issue with the internet connection, this is an error, handle appropriately
            case PNStatusCategory.PNAccessDeniedCategory =>
            // this means that PAM does allow this client to subscribe to this
            // channel and channel group configuration. This is another explicit error
            case _ =>
            // More errors can be directly specified by creating explicit cases for other
            // error categories of `PNStatusCategory` such as `PNTimeoutCategory` or `PNMalformedFilterExpressionCategory` or `PNDecryptionErrorCategory`
          }
        }
        case PNOperationType.PNHeartbeatOperation =>
          // heartbeat operations can in fact have errors, so it is important to check first for an error.
          // For more information on how to configure heartbeat notifications through the status
          // PNObjectEventListener callback, consult <link to the PNCONFIGURATION heartbeart config>
          if (status.isError) {
            // There was an error with the heartbeat operation, handle here
          } else {
            // heartbeat operation was successful
          }
        case _ =>
        // Encountered unknown status type
      }
    }

    def message(pubnub: PubNub, message: PNMessageResult) = {
      logger.warn(message.getChannel + " - " + message.getMessage)
      context.parent ! ExternalMessage(message.getChannel, new DateTime(message.getTimetoken / 10000000 * 1000), message.getMessage.toString)
    }

    def presence(pubnub: PubNub, presence: PNPresenceEventResult) {
      // handle incoming presence data
    }
  })
  pubnub.subscribe.channels(java.util.Arrays.asList(SunTimingChannel, PatternUpdateChannel)).execute

  def getHistoryForChannel(channel: String) = {
    pubnub.history
      .channel(channel)
      .count(100)
      .includeTimetoken(true)
      .async(new PNCallback[PNHistoryResult]() {
      def onResponse(result: PNHistoryResult, status: PNStatus) = {
        if (!status.isError) {
          val messages = result.getMessages.iterator
          while (messages.hasNext) {
            val message = messages.next
            context.parent ! ExternalMessage(channel, new DateTime(message.getTimetoken / 10000000 * 1000), message.getEntry.toString)
          }
        }
      }
    })
  }

  def receive = {
    case message: ExternalMessage => {
      pubnub.publish
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

  getHistoryForChannel(SunTimingChannel)
  getHistoryForChannel(PatternUpdateChannel)

}
