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

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.jmap.core.{Id, Properties, SetError}
import org.apache.james.jmap.mail.UnparsedMailboxId
import org.apache.james.jmap.method.WithoutAccountId
import org.apache.james.task.TaskId
import org.apache.james.task.TaskManager.Status
import play.api.libs.json.JsObject

import scala.util.Try

case class UnparsedFolderFilteringActionId(id: Id) {
  def asTaskId: Either[IllegalArgumentException, TaskId] = FolderFilteringTaskIdUtil.liftOrThrow(this)
}

object UnparsedFolderFilteringActionId {
  def from(taskId: TaskId): UnparsedFolderFilteringActionId = FolderFilteringTaskIdUtil.asUnparsedFolderFilteringActionId(taskId)
}

case class FolderFilteringActionIds(list: List[UnparsedFolderFilteringActionId])

object FolderFilteringTaskIdUtil {
  def asUnparsedFolderFilteringActionId(taskId: TaskId): UnparsedFolderFilteringActionId =
    UnparsedFolderFilteringActionId(Id.validate(taskId.asString()).toOption.get)

  def liftOrThrow(unparsedId: UnparsedFolderFilteringActionId): Either[IllegalArgumentException, TaskId] =
    liftOrThrow(unparsedId.id.value)

  private def liftOrThrow(value: String): Either[IllegalArgumentException, TaskId] =
    Try(TaskId.fromString(value))
      .toEither
      .left.map(e => new IllegalArgumentException("TaskId is invalid", e))
}

case class ProcessedMessageCount(value: UnsignedInt)
case class SuccessfulActions(value: UnsignedInt)
case class FailedActions(value: UnsignedInt)

object FolderFilteringActionGetRequest {
  val allSupportedProperties: Properties = Properties(
    "id", "status", "processedMessageCount", "successfulActions", "failedActions", "maximumAppliedActionReached")
  val idProperty: Properties = Properties("id")
}

case class FolderFilteringActionGetRequest(ids: FolderFilteringActionIds,
                                           properties: Option[Properties]) extends WithoutAccountId {
  def validateProperties: Either[IllegalArgumentException, Properties] =
    properties match {
      case None => Right(FolderFilteringActionGetRequest.allSupportedProperties)
      case Some(value) =>
        value -- FolderFilteringActionGetRequest.allSupportedProperties match {
          case invalidProperties if invalidProperties.isEmpty() => Right(value ++ FolderFilteringActionGetRequest.idProperty)
          case invalidProperties: Properties => Left(new IllegalArgumentException(s"The following properties [${invalidProperties.format()}] are not supported."))
        }
    }
}

object FolderFilteringActionGetResponse {
  def from(list: Seq[FolderFilteringAction], requestIds: FolderFilteringActionIds): FolderFilteringActionGetResponse =
    FolderFilteringActionGetResponse(
      list = list.filter(action => requestIds.list.contains(FolderFilteringTaskIdUtil.asUnparsedFolderFilteringActionId(action.id))),
      notFound = requestIds.list.diff(list.map(action => FolderFilteringTaskIdUtil.asUnparsedFolderFilteringActionId(action.id))).toSet)
}

case class FolderFilteringActionGetResponse(list: Seq[FolderFilteringAction],
                                            notFound: Set[UnparsedFolderFilteringActionId])

object FolderFilteringActionCreation {
  private val knownProperties: Set[String] = Set("mailboxId")

  def validateProperties(jsObject: JsObject): Either[FolderFilteringActionCreationParseException, JsObject] = {
    if (!jsObject.keys.toSet.contains("mailboxId")) {
      return Left(FolderFilteringActionCreationParseException(SetError.invalidArguments(
        SetErrorDescription(s"Missing 'mailboxId' property"))))
    }

    (jsObject.keys.toSet -- knownProperties) match {
      case unknown if unknown.nonEmpty =>
        Left(FolderFilteringActionCreationParseException(SetError.invalidArguments(
          SetErrorDescription(s"Unsupported properties: ${unknown.mkString(", ")}"))))
      case _ => Right(jsObject)
    }
  }
}

case class FolderFilteringActionCreationId(id: Id) {
  def serialize: String = id.value
}

case class FolderFilteringActionCreationRequest(mailboxId: UnparsedMailboxId)

case class FolderFilteringActionSetRequest(create: Option[Map[FolderFilteringActionCreationId, JsObject]]) extends WithoutAccountId

case class FolderFilteringActionCreationResponse(id: TaskId)

case class FolderFilteringActionSetResponse(created: Option[Map[FolderFilteringActionCreationId, FolderFilteringActionCreationResponse]] = None,
                                            notCreated: Option[Map[FolderFilteringActionCreationId, SetError]] = None) extends WithoutAccountId

case class FolderFilteringAction(id: TaskId,
                                 status: Status,
                                 processedMessageCount: ProcessedMessageCount,
                                 successfulActions: SuccessfulActions,
                                 failedActions: FailedActions,
                                 maximumAppliedActionReached: Boolean)

case class FolderFilteringActionCreationParseException(setError: SetError) extends RuntimeException