package com._338oaklandcreations.fabric.machinery

import akka.actor.{ActorLogging, Actor}
import com._338oaklandcreations.fabric.opc.OpcClient
import org.slf4j.LoggerFactory

/**
 * Created by mauricio on 5/30/15.
 */
class OpcActor extends Actor with ActorLogging {

  val logger = LoggerFactory.getLogger(getClass)

  logger.info("Starting OpcActor")
  val server = new OpcClient("127.0.0.1", 7890);
  val fadecandy = server.addDevice();
  val strip = fadecandy.addPixelStrip(0, 64);

  val color = 0xFF0000;  // red
  strip.setPixelColor(3, color);
  strip.setPixelColor(5, 0x888800); // yellow
  strip.setPixelColor(7, 0x00FF00); // green

  server.show();        // Display the pixel changes
  Thread.sleep(5000);   // Wait five seconds
  server.clear();       // Set all pixels to black
  server.show();        // Show the darkened pixels

  server.close();
  logger.info("Ending OpcActor")

  def receive = {
    case _ =>
  }

}
