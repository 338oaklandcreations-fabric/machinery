package org.bustos.tides

import akka.actor.{ Actor, ActorRef, Props, ActorSystem }
import akka.actor.Stash
import akka.actor.ActorLogging

/**
 * Console input controller for Tides
 *
 * This actor helps manage the console input and send it up
 * to the Tides controller.
 */

object TidesConsole {
  case class ConsoleInput(inputString: String)
}

class TidesConsole extends Actor with ActorLogging with Stash {

  import TidesConsole._

  for (ln <- io.Source.stdin.getLines) context.parent ! ConsoleInput(ln)

  def receive = {
    case other =>
  }
}
