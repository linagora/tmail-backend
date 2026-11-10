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

import com.linagora.tmail.james.jmap.json.ValueCodecs.{valueFormat, valueReads, valueWrites}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{Format, JsError, JsNull, JsNumber, JsString, JsSuccess, Reads, Writes}

object ValueCodecsTest {
  case class Name(value: String)
  case class Nickname(value: Option[String])

  val nameFormat: Format[Name] = valueFormat[Name]
  val nameReads: Reads[Name] = valueReads[Name]
  val nicknameWrites: Writes[Nickname] = valueWrites[Nickname]
}

class ValueCodecsTest {
  import ValueCodecsTest._

  @Test
  def writesShouldOutputTheRawValue(): Unit =
    assertThat(nameFormat.writes(Name("alice"))).isEqualTo(JsString("alice"))

  @Test
  def readsShouldWrapTheRawValue(): Unit =
    assertThat(nameFormat.reads(JsString("alice"))).isEqualTo(JsSuccess(Name("alice")))

  @Test
  def readsShouldRejectAnotherType(): Unit =
    assertThat(nameReads.reads(JsNumber(1)).isInstanceOf[JsError]).isTrue

  @Test
  def writesShouldOutputNullForAnEmptyOption(): Unit =
    assertThat(nicknameWrites.writes(Nickname(None))).isEqualTo(JsNull)

  @Test
  def writesShouldOutputTheValueOfAnOption(): Unit =
    assertThat(nicknameWrites.writes(Nickname(Some("al")))).isEqualTo(JsString("al"))
}
