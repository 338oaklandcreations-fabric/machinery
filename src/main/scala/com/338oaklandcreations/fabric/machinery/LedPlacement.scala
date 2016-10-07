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

import java.io.{File, PrintWriter}

import spray.json.DefaultJsonProtocol

trait LedPlacement extends DefaultJsonProtocol {

  case class Point(point: List[Double])
  implicit val pointJson = jsonFormat1(Point)

  val positions: List[(Double, Double)] = null

  val layoutWidth: Int = 0

  def writePositions(fn: String) = {
    val points =
      for {led <- positions
      } yield {
        Point(List(BigDecimal(led._2.toDouble / 10.0).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
          BigDecimal(led._1.toDouble / 10.0).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
          BigDecimal(0.0).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble))
      }
    val writer = new PrintWriter(new File(fn))
    writer.write(points.toList.toJson.toString)
    writer.close()
  }

}
