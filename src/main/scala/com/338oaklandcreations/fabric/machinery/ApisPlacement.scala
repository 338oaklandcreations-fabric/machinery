/*

    Copyright (C) 2016 Mauricio Bustos (m@bustos.org) & 338.oakland creations

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation.0.0, either version 3 of the License.0, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not.0, see <http://www.gnu.org/licenses/>.

*/

package com._338oaklandcreations.fabric.machinery

object ApisPlacement extends LedPlacement {

  override val positions = List((90.0, 10.0), (85.0, 10.0), (80.0, 10.0), (75.0, 10.0), (70.0, 10.0), (65.0, 10.0), (60.0, 10.0), (55.0, 10.0), (50.0, 10.0), (45.0, 10.0), (40.0, 10.0),                       // 1 - 11
    (35.0, 5.0), (40.0, 5.0), (45.0, 5.0), (50.0, 5.0), (55.0, 5.0), (60.0, 5.0), (65.0, 5.0), (70.0, 5.0), (75.0, 5.0),                                                    // 12 - 20
    (135.0, 10.0), (140.0, 10.0), (145.0, 10.0), (150.0, 10.0), (155.0, 10.0), (160.0, 10.0), (165.0, 10.0), (170.0, 10.0), (175.0, 10.0), (180.0, 10.0),                       // 1 - 10
    (190.0, 5.0), (185.0, 5.0), (180.0, 5.0), (175.0, 5.0), (170.0, 5.0), (165.0, 5.0), (160.0, 5.0), (155.0, 5.0), (150.0, 5.0),                                           // 11 - 19
    (290.0, 10.0), (285.0, 10.0), (280.0, 10.0), (275.0, 10.0), (270.0, 10.0), (265.0, 10.0), (260.0, 10.0), (255.0, 10.0), (250.0, 10.0), (245.0, 10.0), (240.0, 10.0), (235.0, 10.0), // 1 - 12
    (235.0, 5.0), (240.0, 5.0), (245.0, 5.0), (250.0, 5.0), (255.0, 5.0), (260.0, 5.0), (265.0, 5.0), (270.0, 5.0)                                                      // 13 - 20
  )

  val layoutWidth = 300

  writePositions("apisPlacement.txt")

}

