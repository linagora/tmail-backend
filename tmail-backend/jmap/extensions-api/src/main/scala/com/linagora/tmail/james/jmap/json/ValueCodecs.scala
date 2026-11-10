/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.json

import play.api.libs.json.{Format, Reads, Writes}

import scala.compiletime.{erasedValue, error, summonInline}
import scala.deriving.Mirror

/**
 * play-json only offers `Json.valueReads`, `Json.valueWrites` and `Json.valueFormat` for `AnyVal` types on Scala 3,
 * while the Scala 2.13 artifact accepted any single-field case class. These derive the same JSON representation
 * (the raw wrapped value) for any single-field case class.
 */
object ValueCodecs {
  inline def valueWrites[T <: Product](using m: Mirror.ProductOf[T]): Writes[T] =
    inline erasedValue[m.MirroredElemTypes] match {
      case _: (h *: EmptyTuple) => Writes(t => summonInline[Writes[h]].writes(t.productElement(0).asInstanceOf[h]))
      case _ => error("valueWrites expects a single-field case class")
    }

  inline def valueReads[T <: Product](using m: Mirror.ProductOf[T]): Reads[T] =
    inline erasedValue[m.MirroredElemTypes] match {
      case _: (h *: EmptyTuple) => Reads(js => summonInline[Reads[h]].reads(js).map(v => m.fromProduct(Tuple1(v))))
      case _ => error("valueReads expects a single-field case class")
    }

  inline def valueFormat[T <: Product](using m: Mirror.ProductOf[T]): Format[T] =
    Format(valueReads[T], valueWrites[T])
}
