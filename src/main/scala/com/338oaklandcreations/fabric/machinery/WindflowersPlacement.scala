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

object WindflowersPlacement {

  val template = List((10, 10), (20, 10), (30, 10))

  def offset(start: List[Tuple2[Int, Int]], offset: Int): List[Tuple2[Int, Int]] = {
    start.map( point => (point._1 + offset, point._2 + offset))
  }

  val positions = offset(template, 40) ++ offset(template, 80)

  val layoutWidth = 300
}