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

import java.time.ZonedDateTime
import java.util

import com.linagora.tmail.james.jmap.json.EmailRecoveryActionSerializer
import com.linagora.tmail.james.jmap.method.AsEitherRequest
import eu.timepit.refined.auto._
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.jmap.core.{Id, Properties, SetError, UTCDate}
import org.apache.james.jmap.method.WithoutAccountId
import org.apache.james.task.TaskId
import org.apache.james.task.TaskManager.Status
import org.apache.james.vault.search.{Criterion, CriterionFactory, Query}
import play.api.libs.json.{JsObject, Json}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.Try

case class EmailRecoveryActionCreationId(id: Id) {
  def serialise: String = id.value
}

case class EmailRecoverySubject(value: String) extends AnyVal {
  def asCriterion: Criterion[String] = CriterionFactory.subject().containsIgnoreCase(value)
}

case class EmailRecoverySender(value: MailAddress) {
  def asCriterion: Criterion[MailAddress] = CriterionFactory.hasSender(value)
}

case class EmailRecoveryRecipient(value: MailAddress) {
  def asCriterion: Criterion[util.Collection[MailAddress]] = CriterionFactory.containsRecipient(value)
}

case class EmailRecoveryHasAttachment(value: Boolean) extends AnyVal {
  def asCriterion: Criterion[java.lang.Boolean] =
    if (value) CriterionFactory.hasAttachment else CriterionFactory.hasNoAttachment
}

case class EmailRecoveryDeletedBefore(value: UTCDate) {
  def asCriterion: Criterion[ZonedDateTime] = {
    CriterionFactory.deletionDate().beforeOrEquals(value.asUTC)
  }
}

case class EmailRecoveryDeletedAfter(value: UTCDate) {
  def asCriterion: Criterion[ZonedDateTime] = {
    CriterionFactory.deletionDate().afterOrEquals(value.asUTC)
  }
}

case class EmailRecoveryReceivedBefore(value: UTCDate) {
  def asCriterion: Criterion[ZonedDateTime] = {
    CriterionFactory.deliveryDate().beforeOrEquals(value.asUTC)
  }
}

case class EmailRecoveryReceivedAfter(value: UTCDate) {
  def asCriterion: Criterion[ZonedDateTime] = {
    CriterionFactory.deliveryDate().afterOrEquals(value.asUTC)
  }
}

object EmailRecoveryActionCreation {
  private val knownProperties: Set[String] = Set("deletedBefore", "deletedAfter", "receivedBefore",
    "receivedAfter", "hasAttachment", "subject", "sender", "recipients")

  def validateProperties(jsObject: JsObject): Either[EmailRecoveryActionCreationParseException, JsObject] = {
    (jsObject.keys.toSet -- knownProperties) match {
      case unknownProperties if unknownProperties.nonEmpty =>
        Left(EmailRecoveryActionCreationParseException(SetError.invalidArguments(
          SetErrorDescription(s"Unknown properties: ${unknownProperties.mkString(", ")}"))))
      case _ => Right(jsObject)
    }
  }
}

case class EmailRecoveryActionCreationRequest(deletedBefore: Option[EmailRecoveryDeletedBefore],
                                              deletedAfter: Option[EmailRecoveryDeletedAfter],
                                              receivedBefore: Option[EmailRecoveryReceivedBefore],
                                              receivedAfter: Option[EmailRecoveryReceivedAfter],
                                              hasAttachment: Option[EmailRecoveryHasAttachment],
                                              subject: Option[EmailRecoverySubject],
                                              sender: Option[EmailRecoverySender],
                                              recipients: Option[Seq[EmailRecoveryRecipient]]) {
  def asQuery(maxEmailRecovery: Long): Query = {
    val criteria: Seq[Criterion[_]] = Seq(
      deletedBefore.map(_.asCriterion),
      deletedAfter.map(_.asCriterion),
      receivedBefore.map(_.asCriterion),
      receivedAfter.map(_.asCriterion),
      hasAttachment.map(_.asCriterion),
      subject.map(_.asCriterion),
      sender.map(_.asCriterion),
      recipients.map(_.map(_.asCriterion)).getOrElse(Seq.empty)).flatten
    Query.and(criteria.asJava, Option(maxEmailRecovery.asInstanceOf[java.lang.Long]).toJava)
  }
}


case class EmailRecoveryActionCreationParseException(setError: SetError) extends Exception

case class EmailRecoveryActionSetRequest(create: Option[Map[EmailRecoveryActionCreationId, JsObject]],
                                         update: Option[Map[UnparsedEmailRecoveryActionId, EmailRecoveryActionUpdatePatchObject]]) extends WithoutAccountId

case class EmailRecoveryActionCreationResponse(id: TaskId)

case class EmailRecoveryActionSetResponse(created: Option[Map[EmailRecoveryActionCreationId, EmailRecoveryActionCreationResponse]] = None,
                                          notCreated: Option[Map[EmailRecoveryActionCreationId, SetError]] = None,
                                          updated: Option[Map[TaskId, EmailRecoveryActionUpdateResponse]] = None,
                                          notUpdated: Option[Map[UnparsedEmailRecoveryActionId, SetError]] = None) extends WithoutAccountId

object UnparsedEmailRecoveryActionId {
  def from(taskId: TaskId): UnparsedEmailRecoveryActionId = TaskIdUtil.asUnparsedEmailRecoveryActionId(taskId)
}

case class UnparsedEmailRecoveryActionId(id: Id) {
  def asTaskId: Either[IllegalArgumentException, TaskId] = TaskIdUtil.liftOrThrow(this)
}

object EmailRecoveryActionUpdateStatus {
  val CANCELED: String = "canceled"
}

case class EmailRecoveryActionUpdateStatus(value: String) extends AnyVal {

  import EmailRecoveryActionUpdateStatus._

  def validate: Either[IllegalArgumentException, EmailRecoveryActionUpdateStatus] =
    value match {
      case CANCELED => scala.Right(this)
      case _ => scala.Left(new IllegalArgumentException(s"Invalid status '$value'"))
    }
}

case class EmailRecoveryActionUpdateRequest(status: EmailRecoveryActionUpdateStatus) {
  def validate: Either[IllegalArgumentException, EmailRecoveryActionUpdateRequest] = status.validate
    .map(_ => this)
}

object EmailRecoveryActionUpdatePatchObject {
  private val knownProperties: Set[String] = Set("status")
}

case class EmailRecoveryActionUpdatePatchObject(jsObject: JsObject) {
  import EmailRecoveryActionUpdatePatchObject._
  def validateProperties: Either[IllegalArgumentException, JsObject] =
    (jsObject.keys.toSet -- knownProperties) match {
      case unknownProperties if unknownProperties.nonEmpty =>
        Left(new IllegalArgumentException(s"Unknown properties: ${unknownProperties.mkString(", ")}"))
      case _ => Right(jsObject)
    }

  def asUpdateRequest: Either[IllegalArgumentException, EmailRecoveryActionUpdateRequest] =
    for {
      validatedJsObject <- validateProperties
      updateRequestSerialize <- EmailRecoveryActionSerializer.deserializeSetUpdateRequest(validatedJsObject)
        .asEitherRequest
      updateRequest <- updateRequestSerialize.validate
    } yield updateRequest
}

case class EmailRecoveryActionUpdateException(description: Option[String] = None, setError: Option[SetError] = None) extends Exception

case class EmailRecoveryActionUpdateResponse(json: JsObject = Json.obj())

case class EmailRecoveryActionIds(list: List[UnparsedEmailRecoveryActionId])

object EmailRecoveryActionGetRequest {
  val allSupportedProperties: Properties = Properties("id", "successfulRestoreCount", "errorRestoreCount", "status")
  val idProperty: Properties = Properties("id")
}

case class EmailRecoveryActionGetRequest(ids: EmailRecoveryActionIds,
                                         properties: Option[Properties]) extends WithoutAccountId {
  def validateProperties: Either[IllegalArgumentException, Properties] =
    properties match {
      case None => Right(EmailRecoveryActionGetRequest.allSupportedProperties)
      case Some(value) =>
        value -- EmailRecoveryActionGetRequest.allSupportedProperties match {
          case invalidProperties if invalidProperties.isEmpty() => Right(value ++ EmailRecoveryActionGetRequest.idProperty)
          case invalidProperties: Properties => Left(new IllegalArgumentException(s"The following properties [${invalidProperties.format()}] are not supported."))
        }
    }
}

object EmailRecoveryActionGetResponse {
  def from(list: Seq[EmailRecoveryAction], requestIds: EmailRecoveryActionIds): EmailRecoveryActionGetResponse =
    EmailRecoveryActionGetResponse(
      list = list.filter(emailRecoveryAction => requestIds.list.contains(TaskIdUtil.asUnparsedEmailRecoveryActionId(emailRecoveryAction.id))),
      notFound = requestIds.list.diff(list.map(emailRecoveryAction => TaskIdUtil.asUnparsedEmailRecoveryActionId(emailRecoveryAction.id))).toSet)
}

case class EmailRecoveryActionGetResponse(list: Seq[EmailRecoveryAction],
                                          notFound: Set[UnparsedEmailRecoveryActionId])

case class SuccessfulRestoreCount(value: UnsignedInt)

case class ErrorRestoreCount(value: UnsignedInt)

object TaskIdUtil {
  def asUnparsedEmailRecoveryActionId(taskId: TaskId): UnparsedEmailRecoveryActionId =
    UnparsedEmailRecoveryActionId(Id.validate(taskId.asString()).toOption.get)

  def liftOrThrow(unparsedId: UnparsedEmailRecoveryActionId): Either[IllegalArgumentException, TaskId] =
    liftOrThrow(unparsedId.id.value)

  private def liftOrThrow(value: String): Either[IllegalArgumentException, TaskId] =
    Try(TaskId.fromString(value))
      .toEither
      .left.map(e => new IllegalArgumentException("TaskId is invalid", e))
}

case class EmailRecoveryAction(id: TaskId,
                               successfulRestoreCount: SuccessfulRestoreCount,
                               errorRestoreCount: ErrorRestoreCount,
                               status: Status)