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

object ReedsPlacement extends DefaultJsonProtocol {

  case class Point(point: List[Double])
  implicit val pointJson = jsonFormat1(Point)

  val positions = List((90, 10), (85, 10), (80, 10), (75, 10), (70, 10), (65, 10), (60, 10), (55, 10), (50, 10), (45, 10), (40, 10),                       // 1 - 11
                       (35, 5), (40, 5), (45, 5), (50, 5), (55, 5), (60, 5), (65, 5), (70, 5), (75, 5),                                                    // 12 - 20
                       (135, 10), (140, 10), (145, 10), (150, 10), (155, 10), (160, 10), (165, 10), (170, 10), (175, 10), (180, 10),                       // 1 - 10
                       (190, 5), (185, 5), (180, 5), (175, 5), (170, 5), (165, 5), (160, 5), (155, 5), (150, 5),                                           // 11 - 19
                       (290, 10), (285, 10), (280, 10), (275, 10), (270, 10), (265, 10), (260, 10), (255, 10), (250, 10), (245, 10), (240, 10), (235, 10), // 1 - 12
                       (235, 5), (240, 5), (245, 5), (250, 5), (255, 5), (260, 5), (265, 5), (270, 5)                                                      // 13 - 20
  )

  val layoutWidth = 300

  val points =
    for {led <- positions
    } yield {
      Point(List(BigDecimal(led._2 / 10.0).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
        BigDecimal(led._1 / 10.0).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble,
        BigDecimal(0.0).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble))
    }
  val writer = new PrintWriter(new File("fabricGrid.txt"))
  writer.write(points.toList.toJson.toString)
  writer.close()

}


