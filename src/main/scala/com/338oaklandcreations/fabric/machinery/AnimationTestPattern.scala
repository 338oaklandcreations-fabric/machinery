package com._338oaklandcreations.fabric.machinery

import com._338oaklandcreations.fabric.opc.{PixelStrip, Animation}
import com._338oaklandcreations.fabric.opc.Animation.makeColor
import java.util.Random

/**
 * Created by mauricio on 6/1/15.
 */
class AnimationTestPattern extends Animation {

  var rand: Random = new Random

  def reset(strip: PixelStrip) {
    rand = new Random
  }

  def draw(strip: PixelStrip): Boolean = {
    val randomPixel = rand.nextInt(strip.getPixelCount)
    val randomColor = makeColor(rand.nextInt(255), rand.nextInt(255), rand.nextInt(255));
    strip.setPixelColor(randomPixel, randomColor);
    true
  }
}
