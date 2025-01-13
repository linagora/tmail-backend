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

import com.linagora.tmail.james.jmap.model.{HasMoreChanges, LabelChangesRequest, LabelChangesResponse, LabelId}
import org.apache.james.jmap.api.change.Limit
import org.apache.james.jmap.core.UuidState
import play.api.libs.json.{Format, JsError, JsNumber, JsObject, JsResult, JsSuccess, JsValue, Json, Reads, Writes}

object LabelChangesSerializer {
  private implicit val labelIdFormat: Format[LabelId] = Json.valueFormat[LabelId]
  private implicit val limitReads: Reads[Limit] = {
    case JsNumber(value) => JsSuccess(Limit.of(value.intValue))
    case _ => JsError("Limit must be an integer")
  }
  private implicit val stateReads: Reads[UuidState] = Json.valueReads[UuidState]
  private implicit val labelChangesRequestReads: Reads[LabelChangesRequest] = Json.reads[LabelChangesRequest]
  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val hasMoreChangesWrites: Writes[HasMoreChanges] = Json.valueWrites[HasMoreChanges]
  private implicit val labelChangesResponseWrites: Writes[LabelChangesResponse] =
    response => Json.obj(
      "accountId" -> response.accountId,
      "oldState" -> response.oldState,
      "newState" -> response.newState,
      "hasMoreChanges" -> response.hasMoreChanges,
      "created" -> response.created,
      "updated" -> response.updated,
      "destroyed" -> response.destroyed)

  def deserializeRequest(input: JsValue): JsResult[LabelChangesRequest] = Json.fromJson[LabelChangesRequest](input)

  def serialize(response: LabelChangesResponse): JsObject =
    Json.toJson(response).as[JsObject]
}
