package com.linagora.tmail.james.jmap.model

import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{SetError, UTCDate}
import org.apache.james.jmap.method.WithoutAccountId
import org.apache.james.task.TaskId
import org.apache.james.vault.search.{Criterion, CriterionFactory, Query}
import play.api.libs.json.JsObject

import java.time.ZonedDateTime
import java.util
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
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

case class EmailRecoveryActionSetRequest(create: Option[Map[EmailRecoveryActionCreationId, JsObject]]) extends WithoutAccountId

case class EmailRecoveryActionCreationResponse(id: TaskId)

case class EmailRecoveryActionSetResponse(created: Option[Map[EmailRecoveryActionCreationId, EmailRecoveryActionCreationResponse]] = None,
                                          notCreated: Option[Map[EmailRecoveryActionCreationId, SetError]] = None)