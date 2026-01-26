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

import com.linagora.tmail.james.jmap.model.LabelPatchObject.{colorProperty, displayNameProperty, documentationProperty, updateProperties}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, Properties, SetError, UuidState}
import org.apache.james.jmap.mail.Keyword
import org.apache.james.jmap.method.WithAccountId
import play.api.libs.json.{JsObject, JsString, JsValue, Json}

case class LabelSetRequest(accountId: AccountId,
                           create: Option[Map[LabelCreationId, JsObject]],
                           update: Option[Map[UnparsedLabelId, LabelPatchObject]],
                           destroy: Option[Seq[UnparsedLabelId]]) extends WithAccountId

case class LabelCreationId(id: Id)

case class LabelCreationResponse(id: LabelId,
                                 keyword: Keyword)

case class LabelSetResponse(accountId: AccountId,
                            oldState: Option[UuidState],
                            newState: UuidState,
                            created: Option[Map[LabelCreationId, LabelCreationResponse]],
                            notCreated: Option[Map[LabelCreationId, SetError]],
                            updated: Option[Map[LabelId, LabelUpdateResponse]],
                            notUpdated: Option[Map[UnparsedLabelId, SetError]],
                            destroyed: Option[Seq[LabelId]],
                            notDestroyed: Option[Map[UnparsedLabelId, SetError]])

object LabelPatchObject {
  private val displayNameProperty: String = "displayName"
  private val colorProperty: String = "color"
  private val documentationProperty: String = "documentation"
  private val updateProperties: Set[String] = Set(displayNameProperty, colorProperty)
}

case class LabelPatchObject(value: Map[String, JsValue]) {
  def validate(): Either[LabelPatchUpdateValidationException, ValidatedLabelPatchObject] =
    for {
      _ <- validateProperties
      displayName <- validateDisplayName
      color <- validateColor
      documentation <- validateDocumentation
    } yield {
      ValidatedLabelPatchObject(displayName, color, documentation)
    }

  def validateProperties: Either[LabelPatchUpdateValidationException, LabelPatchObject] =
    value.find(mapEntry => !updateProperties.contains(mapEntry._1))
      .map(e => Left(LabelPatchUpdateValidationException("Some unknown properties were specified", Some(e._1))))
      .getOrElse(Right(this))

  private def validateColor: Either[LabelPatchUpdateValidationException, Option[Color]] =
    value.get(colorProperty) match {
      case Some(jsValue) => jsValue match {
        case JsString(aString) => Color.validate(aString) match {
          case Right(color: Color) => Right(Some(color))
          case Left(e: IllegalArgumentException) => Left(LabelPatchUpdateValidationException(e.getMessage, Some(colorProperty)))
        }
        case _ => Left(LabelPatchUpdateValidationException("Expecting a JSON string as an argument", Some(colorProperty)))
      }
      case None => Right(None)
    }

  private def validateDocumentation: Either[LabelPatchUpdateValidationException, Option[String]] =
    value.get(documentationProperty) match {
      case Some(jsValue) => jsValue match {
        case JsString(aString) => Right(Some(aString))
        case _ => Left(LabelPatchUpdateValidationException("Expecting a JSON string as an argument", Some(documentationProperty)))
      }
      case None => Right(None)
    }

  private def validateDisplayName: Either[LabelPatchUpdateValidationException, Option[DisplayName]] =
    value.get(displayNameProperty) match {
      case Some(jsValue) => jsValue match {
        case JsString(aString) => Right(Some(DisplayName(aString)))
        case _ => Left(LabelPatchUpdateValidationException("Expecting a JSON string as an argument", Some(displayNameProperty)))
      }
      case None => Right(None)
    }
}

case class ValidatedLabelPatchObject(displayNameUpdate: Option[DisplayName] = None,
                                     colorUpdate: Option[Color] = None,
                                     documentationUpdate: Option[String] = None)

case class LabelUpdateResponse(json: JsObject = Json.obj())

case class LabelPatchUpdateValidationException(error: String, property: Option[String] = None) extends IllegalArgumentException {
  def asProperty: Option[Properties] = property.map(value => Properties.toProperties(Set(value)))
}