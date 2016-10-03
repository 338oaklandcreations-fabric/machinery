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

object WindflowersPlacement extends LedPlacement {

  def circularPositions(count: Int, offset: Double): List[(Double, Double)] = {
    (0 to count - 1).map(x => {
      (offset * Math.sin(2.0 * Math.PI / count * x), offset * Math.cos(2.0 * Math.PI / count * x))
    }).toList
  }

  val template = List(
    (0.0, 0.0), (0.0, 5.0), (0.0, 10.0), (0.0, 15.0),
    (10.0, -10.0), (10.0, -5.0), (10.0, 0.0), (10.0, 5.0), (10.0, 10.0), (10.0, 15.0), (10.0, 20.0), (10.0, 25.0),
    (20.0, -25.0), (20.0, -20.0), (20.0, -15.0), (20.0, -10.0), (20.0, -5.0), (20.0, 0.0), (20.0, 5.0), (20.0, 10.0), (20.0, 15.0), (20.0, 20.0), (20.0, 25.0), (20.0, 30.0), (20.0, 35.0), (20.0, 40.0), (20.0, 45.0),
    (30.0, -25.0), (30.0, -20.0), (30.0, -15.0), (30.0, -10.0), (30.0, -5.0), (30.0, 0.0), (30.0, 5.0), (30.0, 10.0), (30.0, 15.0), (30.0, 20.0), (30.0, 25.0), (30.0, 30.0), (30.0, 35.0), (30.0, 40.0), (30.0, 45.0),
    (40.0, -10.0), (40.0, -5.0), (40.0, 0.0), (40.0, 5.0), (40.0, 10.0), (40.0, 15.0), (40.0, 20.0), (40.0, 25.0),
    (50.0, 0.0), (50.0, 5.0), (50.0, 10.0), (50.0, 15.0))

  //val template = List((0.0,0.0)) ++ circularPositions(3, 1.0) ++ circularPositions(6, 2.0) ++ circularPositions(14, 4.0) ++ circularPositions(26, 6.0)

  def offset(start: List[(Double, Double)], offsetPoint: (Int, Int)): List[(Double, Double)] = {
    start.map( point => (point._1 / 5.0 + offsetPoint._1.toDouble, point._2 / 5.0 + offsetPoint._2.toDouble))
  }

  override val positions = offset(template, (20, 0)) ++ offset(template, (35, 15)) ++ offset(template, (50, 0)) ++ offset(template, (65, 15)) ++ offset(template, (80, 0))

  val layoutWidth = 100

  writePositions("windflowersPlacement.txt")

}