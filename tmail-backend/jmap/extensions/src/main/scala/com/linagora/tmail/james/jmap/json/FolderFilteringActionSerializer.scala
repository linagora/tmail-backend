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

import com.linagora.tmail.james.jmap.model._
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.james.jmap.json.mapWrites
import org.apache.james.jmap.mail.UnparsedMailboxId
import org.apache.james.task.TaskId
import org.apache.james.task.TaskManager.Status
import play.api.libs.json._

object FolderFilteringActionSerializer {

  private implicit val idFormat: Format[UnparsedFolderFilteringActionId] = Json.valueFormat[UnparsedFolderFilteringActionId]
  private implicit val folderFilteringActionIdsReads: Reads[FolderFilteringActionIds] = Json.valueReads[FolderFilteringActionIds]
  private implicit val folderFilteringActionGetRequestReads: Reads[FolderFilteringActionGetRequest] = Json.reads[FolderFilteringActionGetRequest]

  private implicit val creationIdReads: Reads[FolderFilteringActionCreationId] = Json.valueReads[FolderFilteringActionCreationId]
  private implicit val mapCreationRequestReads: Reads[Map[FolderFilteringActionCreationId, JsObject]] =
    Reads.mapReads[FolderFilteringActionCreationId, JsObject](s => creationIdReads.reads(JsString(s)))
  private implicit val unparsedMailboxIdReads: Reads[UnparsedMailboxId] = Json.valueReads[UnparsedMailboxId]
  private implicit val folderFilteringActionCreationRequestReads: Reads[FolderFilteringActionCreationRequest] = Json.reads[FolderFilteringActionCreationRequest]
  private implicit val folderFilteringActionSetRequestReads: Reads[FolderFilteringActionSetRequest] = Json.reads[FolderFilteringActionSetRequest]

  private implicit val taskIdWrites: Writes[TaskId] = value => JsString(value.asString())
  private implicit val statusWrites: Writes[Status] = status => JsString(status.getValue)
  private implicit val processedMessageCountWrites: Writes[ProcessedMessageCount] = Json.valueWrites[ProcessedMessageCount]
  private implicit val successfulActionsWrites: Writes[SuccessfulActions] = Json.valueWrites[SuccessfulActions]
  private implicit val failedActionsWrites: Writes[FailedActions] = Json.valueWrites[FailedActions]
  private implicit val folderFilteringActionWrites: Writes[FolderFilteringAction] = (ffa: FolderFilteringAction) =>
    Json.obj(
      "id" -> ffa.id,
      "status" -> ffa.status,
      "processedMessageCount" -> ffa.processedMessageCount,
      "successfulActions" -> ffa.successfulActions,
      "failedActions" -> ffa.failedActions,
      "maximumAppliedActionReached" -> ffa.maximumAppliedActionReached)

  private implicit val folderFilteringActionGetResponseWrites: Writes[FolderFilteringActionGetResponse] = Json.writes[FolderFilteringActionGetResponse]

  private implicit val creationIdWrites: Writes[FolderFilteringActionCreationId] = id => JsString(id.id.value)
  private implicit val creationResponseWrites: Writes[FolderFilteringActionCreationResponse] = Json.writes[FolderFilteringActionCreationResponse]
  private implicit val mapCreationResponseWrites: Writes[Map[FolderFilteringActionCreationId, FolderFilteringActionCreationResponse]] =
    mapWrites[FolderFilteringActionCreationId, FolderFilteringActionCreationResponse](_.serialize, creationResponseWrites)
  private implicit val mapSetErrorForCreationWrites: Writes[Map[FolderFilteringActionCreationId, SetError]] =
    mapWrites[FolderFilteringActionCreationId, SetError](_.serialize, Json.writes[SetError])
  private implicit val setResponseWrites: OWrites[FolderFilteringActionSetResponse] = Json.writes[FolderFilteringActionSetResponse]

  def deserializeGetRequest(input: JsValue): JsResult[FolderFilteringActionGetRequest] = Json.fromJson[FolderFilteringActionGetRequest](input)

  def deserializeSetCreationRequest(input: JsValue): JsResult[FolderFilteringActionCreationRequest] = Json.fromJson[FolderFilteringActionCreationRequest](input)

  def deserializeSetRequest(input: JsValue): JsResult[FolderFilteringActionSetRequest] = Json.fromJson[FolderFilteringActionSetRequest](input)

  def serializeGetResponse(response: FolderFilteringActionGetResponse, properties: Properties): JsValue =
    Json.toJson(response)
      .transform((__ \ "list").json.update {
        case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
          case jsonObject: JsObject => properties
            .filter(jsonObject)
          case jsValue => jsValue
        }))
      }).get

  def serializeSetResponse(response: FolderFilteringActionSetResponse): JsObject = Json.toJsObject(response)
}
