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

import com.linagora.tmail.james.jmap.contact.{ContactOwner, EmailAddressContactMessage, MessageEntry, TmailContactMessageScope, TmailContactMessageType}
import org.apache.james.core.MailAddress
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsError, JsPath, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes}

import scala.util.{Failure, Success, Try}

object EmailAddressContactMessageSerializer {
  implicit val mailAddressReads: Reads[MailAddress] = {
    case JsString(value) => Try(JsSuccess(new MailAddress(value)))
      .fold(e => JsError(s"Invalid mailAddress: ${e.getMessage}"), mailAddress => mailAddress)
    case _ => JsError("Expecting mailAddress to be represented by a JsString")
  }

  implicit val mailAddressWrites: Writes[MailAddress] = mail => JsString(mail.toString)

  private implicit val contactMessageTypeReads: Reads[TmailContactMessageType] = {
    case JsString(value) => TmailContactMessageType.from(value)
      .fold(error => JsError(error.getMessage),
        messageType => JsSuccess(messageType))
    case _ => JsError("`type` needs to be represented by a string")
  }

  private implicit val contactMessageScopeReads: Reads[TmailContactMessageScope] = {
    case JsString(value) => TmailContactMessageScope.from(value)
      .fold(error => JsError(error.getMessage),
        scope => JsSuccess(scope))
    case _ => JsError("`scope` needs to be represented by a string")
  }

  private implicit val contactOwnerReads: Reads[ContactOwner] = Json.valueReads[ContactOwner]

  private implicit val messageEntryReads: Reads[MessageEntry] = Json.reads[MessageEntry]

  private implicit val emailAddressContactMessageReads: Reads[EmailAddressContactMessage] = (
    (JsPath \ "type").read[TmailContactMessageType] and
      (JsPath \ "scope").read[TmailContactMessageScope] and
      (JsPath \ "owner").read[ContactOwner] and
      (JsPath \ "entry").read[MessageEntry]
    ) (EmailAddressContactMessage.apply _)

  def deserializeEmailAddressContactMessage(input: JsValue): JsResult[EmailAddressContactMessage] = Json.fromJson[EmailAddressContactMessage](input)

  def deserializeEmailAddressContactMessageAsJava(input: String): EmailAddressContactMessage =
    Try(Json.parse(input))
      .map(jsValue => deserializeEmailAddressContactMessage(jsValue) match {
        case JsError(errors) => throw new IllegalArgumentException("Can not deserialize. " + errors.toString())
        case JsSuccess(value, _) => value
      })
    match {
      case Success(value) => value
      case Failure(exception) => throw new IllegalArgumentException("Input is not a json. " + exception.getMessage)
    }
}
