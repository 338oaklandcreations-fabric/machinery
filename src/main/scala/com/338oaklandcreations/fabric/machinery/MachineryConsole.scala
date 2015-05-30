package com._338oaklandcreations.fabric.machinery

import akka.actor.{ Actor, ActorRef, Props, ActorSystem }
import akka.actor.Stash
import akka.actor.ActorLogging

/**
 * Console input controller for Tides
 *
 * This actor helps manage the console input and send it up
 * to the Tides controller.
 */

object MachineryConsole {
  case class ConsoleInput(inputString: String)
}

class MachineryConsole extends Actor with ActorLogging with Stash {

  import MachineryConsole._

  for (ln <- io.Source.stdin.getLines) context.parent ! ConsoleInput(ln)

  def receive = {
    case other =>
  }
}
