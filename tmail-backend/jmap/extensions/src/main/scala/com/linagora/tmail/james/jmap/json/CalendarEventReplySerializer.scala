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

import com.linagora.tmail.james.jmap.model.CalendarEventParse.UnparsedBlobId
import com.linagora.tmail.james.jmap.model.{CalendarEventNotDone, CalendarEventNotFound, CalendarEventReplyAcceptedResponse, CalendarEventReplyMaybeResponse, CalendarEventReplyRejectedResponse, CalendarEventReplyRequest, CalendarEventReplyResponse, LanguageLocation}
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.json.mapWrites
import org.apache.james.jmap.mail.{BlobId, BlobIds}
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{Format, JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes}

object CalendarEventReplySerializer {

  private implicit val blobIdReads: Reads[BlobId] = Json.valueReads[BlobId]
  private implicit val blobIdsWrites: Format[BlobIds] = Json.valueFormat[BlobIds]
  private implicit val calendarEventNotFoundWrites: Writes[CalendarEventNotFound] = Json.valueWrites[CalendarEventNotFound]
  private implicit val mapSetErrorWrites: Writes[Map[UnparsedBlobId, SetError]] =
    mapWrites[UnparsedBlobId, SetError](_.value, setErrorWrites)
  private implicit val calendarEventNotDoneWrites: Writes[CalendarEventNotDone] = Json.valueWrites[CalendarEventNotDone]

  private implicit val languageLocationReads: Reads[LanguageLocation] = {
    case JsString(value) => LanguageLocation.fromString(value)
      .fold(e => JsError(e.getMessage),
        languageLocation => JsSuccess(languageLocation))
    case _ => JsError("language needs to be represented with a JsString")
  }

  private implicit val calendarEventReplyRequestReads: Reads[CalendarEventReplyRequest] = Json.reads[CalendarEventReplyRequest]

  private implicit val calendarEventReplyResponseWrites: Writes[CalendarEventReplyResponse] = new Writes[CalendarEventReplyResponse] {
    def writes(response: CalendarEventReplyResponse): JsObject = response match {
      case accepted: CalendarEventReplyAcceptedResponse => writeResponse(accepted, "Accepted")
      case rejected: CalendarEventReplyRejectedResponse => writeResponse(rejected, "Rejected")
      case maybe: CalendarEventReplyMaybeResponse => writeResponse(maybe, "Maybe")
    }

    private def writeResponse(response: CalendarEventReplyResponse, donePropertyName: String): JsObject = {
      val done: Option[(String, JsValueWrapper)] =
        if (response.done.value.nonEmpty) Some(donePropertyName.toLowerCase -> response.done) else None

      val notFound: Option[(String, JsValueWrapper)] =
        response.notFound.flatMap(value => if (value.value.nonEmpty) Some("notFound" -> response.notFound) else None)

      val notDone: Option[(String, JsValueWrapper)] =
        response.notDone.flatMap(value => if (value.value.nonEmpty) Some("not" + donePropertyName -> response.notDone) else None)

      val accountIdJsValue: Option[(String, JsValueWrapper)] = Some("accountId" -> response.accountId)

      Json.obj(Seq(accountIdJsValue, done, notFound, notDone).flatten.toArray: _*)
    }
  }

  def deserializeRequest(input: JsValue): JsResult[CalendarEventReplyRequest] = Json.fromJson[CalendarEventReplyRequest](input)

  def serialize(response: CalendarEventReplyResponse): JsValue = Json.toJson(response)

}
