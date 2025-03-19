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
import com.linagora.tmail.james.jmap.model.{CalendarEventCounterAcceptRequest, CalendarEventCounterAcceptedResponse, CalendarEventNotDone, CalendarEventNotFound}
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.json.mapWrites
import org.apache.james.jmap.mail.{BlobId, BlobIds}
import play.api.libs.json.{Format, JsResult, JsValue, Json, Reads, Writes}

object CalendarEventCounterSerializer {
  private implicit val blobIdReads: Reads[BlobId] = Json.valueReads[BlobId]
  private implicit val blobIdsWrites: Format[BlobIds] = Json.valueFormat[BlobIds]
  private implicit val calendarEventNotFoundWrites: Writes[CalendarEventNotFound] = Json.valueWrites[CalendarEventNotFound]
  private implicit val mapSetErrorWrites: Writes[Map[UnparsedBlobId, SetError]] =
    mapWrites[UnparsedBlobId, SetError](_.value, setErrorWrites)
  private implicit val calendarEventNotDoneWrites: Writes[CalendarEventNotDone] = Json.valueWrites[CalendarEventNotDone]

  private implicit val calendarEventCounterAcceptRequestReads: Reads[CalendarEventCounterAcceptRequest] = Json.reads[CalendarEventCounterAcceptRequest]
  private implicit val calendarEventCounterAcceptedResponseWrites: Writes[CalendarEventCounterAcceptedResponse] = Json.writes[CalendarEventCounterAcceptedResponse]

  def deserializeAcceptRequest(input: JsValue): JsResult[CalendarEventCounterAcceptRequest] = Json.fromJson[CalendarEventCounterAcceptRequest](input)

  def serialize(response: CalendarEventCounterAcceptedResponse): JsValue = Json.toJson(response)
}
