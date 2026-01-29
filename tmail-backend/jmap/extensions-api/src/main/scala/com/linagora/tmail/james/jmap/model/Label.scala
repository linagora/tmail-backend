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

package com.linagora.tmail.james.jmap.model

import java.util.UUID

import eu.timepit.refined
import eu.timepit.refined.auto._
import eu.timepit.refined.string.MatchesRegex
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, Id, Properties, SetError, UuidState}
import org.apache.james.jmap.mail.Keyword
import org.apache.james.jmap.method.WithAccountId

case class LabelCreationParseException(setError: SetError) extends Exception

object LabelId {
  def fromKeyword(keyword: Keyword): LabelId =
    LabelId(Id.validate(keyword.flagName).toOption.get)

  def generate(): LabelId =
    LabelId(Id.validate(UUID.randomUUID().toString).toOption.get)
}

case class LabelId(id: Id) {
  def toKeyword: Keyword =
    Keyword.of(id.value).get

  def asUnparsedLabelId: UnparsedLabelId =
    UnparsedLabelId(id)

  def serialize: String = id.value
}

object KeywordUtil {
  def generate(): Keyword =
    Keyword.of(UUID.randomUUID().toString).get
}

case class DisplayName(value: String)

object Color {
  private type ColorRegex = MatchesRegex["^#[a-fA-F0-9]{6}$"]

  def validate(string: String): Either[IllegalArgumentException, Color] =
    refined.refineV[ColorRegex](string) match {
      case Left(_) => scala.Left(new IllegalArgumentException(s"The string should be a valid hexadecimal color value following this pattern #[a-fA-F0-9]{6}"))
      case Right(value) => scala.Right(Color(value))
    }
}

case class Color(value: String)

case class DescriptionUpdate(value: Option[String])

object LabelCreationRequest {
  val serverSetProperty = Set("id", "keyword")
  val assignableProperties = Set("displayName", "color", "description")
  val knownProperties = assignableProperties ++ serverSetProperty
}

case class LabelCreationRequest(displayName: DisplayName, color: Option[Color], description: Option[String]) {
  def toLabel: Label = {
    val keyword: Keyword = KeywordUtil.generate()

    Label(id = LabelId.fromKeyword(keyword),
      displayName = displayName,
      keyword = keyword,
      color = color,
      description = description)
  }
}

object Label {
  val allProperties: Properties = Properties("id", "displayName", "keyword", "color", "description")
  val idProperty: Properties = Properties("id")
}

case class Label(id: LabelId, displayName: DisplayName, keyword: Keyword, color: Option[Color], description: Option[String] ) {
  def update(newDisplayName: Option[DisplayName], newColor: Option[Color], newDescription:Option[String]): Label =
    copy(displayName = newDisplayName.getOrElse(displayName),
      color = newColor.orElse(color),
      description = newDescription.orElse(description))
}

case class LabelNotFoundException(id: LabelId) extends RuntimeException

case class UnparsedLabelId(id: Id) {
  def asLabelId: LabelId = LabelId(id)
}

case class LabelIds(list: List[UnparsedLabelId])