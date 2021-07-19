package com.linagora.tmail.james.jmap.model

import cats.implicits.toTraverseOps
import com.linagora.tmail.james.jmap.json.EmailSendSerializer
import com.linagora.tmail.james.jmap.model.EmailSendCreationRequestRaw.{emailCreateAssignableProperties, emailSubmissionAssignableProperties}
import eu.timepit.refined.auto._
import eu.timepit.refined.refineV
import org.apache.james.jmap.core.Id.{Id, IdConstraint}
import org.apache.james.jmap.core.Properties.toProperties
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Id, Properties, SetError, UTCDate, UuidState}
import org.apache.james.jmap.json.EmailSetSerializer
import org.apache.james.jmap.mail.Email.Size
import org.apache.james.jmap.mail.{BlobId, DestroyIds, EmailCreationRequest, EmailCreationResponse, EmailSet, EmailSetRequest, EmailSubmissionId, Envelope, ThreadId, UnparsedMessageId}
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MessageId
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.message.DefaultMessageWriter
import org.apache.james.server.core.MimeMessageSource
import play.api.libs.json.{JsError, JsObject, JsPath, JsSuccess, JsonValidationError}

import java.io.{ByteArrayInputStream, InputStream}
import java.time.ZonedDateTime
import java.util.{Date, UUID}
import javax.mail.Flags

object EmailCreationRequest {
  def asAppendCommand(request: EmailCreationRequest, message: Message): AppendCommand =
    AppendCommand.builder()
      .recent()
      .withFlags(request.keywords.map(_.asFlags).getOrElse(new Flags()))
      .withInternalDate(Date.from(request.receivedAt.getOrElse(UTCDate(ZonedDateTime.now())).asUTC.toInstant))
      .build(message)
}

case class EmailSendCreationId(id: Id)

case class EmailSendRequest(accountId: AccountId,
                            create: Map[EmailSendCreationId, JsObject],
                            onSuccessUpdateEmail: Option[Map[EmailSendCreationId, JsObject]],
                            onSuccessDestroyEmail: Option[List[EmailSendCreationId]]) extends WithAccountId {

  def validate: Either[IllegalArgumentException, EmailSendRequest] = {
    val supportedCreationIds: List[EmailSendCreationId] = create.keys.toList

    validateOnSuccessUpdateEmail(supportedCreationIds)
      .flatMap(_ => validateOnSuccessDestroyEmail(supportedCreationIds))
  }

  private def validateOnSuccessUpdateEmail(supportedCreationIds: List[EmailSendCreationId]): Either[IllegalArgumentException, EmailSendRequest] =
    onSuccessUpdateEmail.getOrElse(Map())
      .keys
      .toList
      .map(id => validate(id, supportedCreationIds))
      .sequence
      .map(_ => this)

  private def validateOnSuccessDestroyEmail(supportedCreationIds: List[EmailSendCreationId]): Either[IllegalArgumentException, EmailSendRequest] =
    onSuccessDestroyEmail.getOrElse(List())
      .map(id => validate(id, supportedCreationIds))
      .sequence
      .map(_ => this)

  private def validate(creationId: EmailSendCreationId, supportedCreationIds: List[EmailSendCreationId]): Either[IllegalArgumentException, EmailSendCreationId] = {
    if (creationId.id.value.startsWith("#")) {
      val realId: String = creationId.id.value.substring(1)
      val validatedId: Either[String, Id] = refineV[IdConstraint](realId)
      validatedId
        .left.map(s => new IllegalArgumentException(s))
        .flatMap(id => if (supportedCreationIds.contains(EmailSendCreationId(id))) {
          scala.Right(EmailSendCreationId(id))
        } else {
          Left(new IllegalArgumentException(s"${creationId.id} cannot be referenced in current method call"))
        })
    } else {
      Left(new IllegalArgumentException(s"${creationId.id} cannot be retrieved as storage for Email/send is not yet implemented"))
    }
  }

  def implicitEmailSetRequest(messageIdResolver: EmailSendCreationId => Either[IllegalArgumentException, Option[MessageId]]): Either[IllegalArgumentException, Option[EmailSetRequest]] =
    for {
      update <- resolveOnSuccessUpdateEmail(messageIdResolver)
      destroy <- resolveOnSuccessDestroyEmail(messageIdResolver)
    } yield {
      if (update.isEmpty && destroy.isEmpty) {
        None
      } else {
        Some(EmailSetRequest(
          accountId = accountId,
          create = None,
          update = update,
          destroy = destroy.map(DestroyIds(_))))
      }
    }

  def resolveOnSuccessUpdateEmail(messageIdResolver: EmailSendCreationId => Either[IllegalArgumentException, Option[MessageId]]): Either[IllegalArgumentException, Option[Map[UnparsedMessageId, JsObject]]] =
    onSuccessUpdateEmail.map(map => map.toList
      .map {
        case (creationId, json) => messageIdResolver.apply(creationId).map(msgOpt => msgOpt.map(messageId => (EmailSet.asUnparsed(messageId), json)))
      }
      .sequence
      .map(list => list.flatten.toMap))
      .sequence
      .map {
        case Some(value) if value.isEmpty => None
        case any => any
      }

  def resolveOnSuccessDestroyEmail(messageIdResolver: EmailSendCreationId => Either[IllegalArgumentException, Option[MessageId]]): Either[IllegalArgumentException, Option[List[UnparsedMessageId]]] =
    onSuccessDestroyEmail.map(list => list
      .map(creationId => messageIdResolver.apply(creationId).map(messageIdOpt => messageIdOpt.map(messageId => EmailSet.asUnparsed(messageId))))
      .sequence
      .map(list => list.flatten))
      .sequence
}

object EmailSendCreationRequestInvalidException {
  def parse(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): EmailSendCreationRequestInvalidException = {
    val setError: SetError = errors.head match {
      case (path, Seq()) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in EmailSend object is not valid"))
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) => SetError.invalidArguments(SetErrorDescription(s"Missing '$path' property in EmailSend object"))
      case (path, Seq(JsonValidationError(Seq(message)))) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in EmailSend object is not valid: $message"))
      case (path, _) => SetError.invalidArguments(SetErrorDescription(s"Unknown error on property '$path'"))
    }
    EmailSendCreationRequestInvalidException(setError)
  }
}

case class EmailSendCreationRequestInvalidException(error: SetError) extends Exception

object EmailSendCreationRequest {
  private val assignableProperties: Set[String] = Set("email/create", "emailSubmission/set")

  def validateProperties(jsObject: JsObject): Either[EmailSendCreationRequestInvalidException, JsObject] =
    jsObject.keys.diff(assignableProperties) match {
      case unknownProperties if unknownProperties.nonEmpty =>
        Left(EmailSendCreationRequestInvalidException(SetError.invalidArguments(
          SetErrorDescription("Some unknown properties were specified"),
          Some(Properties.toProperties(unknownProperties.toSet)))))
      case _ => scala.Right(jsObject)
    }
}

object EmailSendCreationRequestRaw {
  private val emailSubmissionAssignableProperties: Set[String] = Set("envelope", "identityId", "onSuccessUpdateEmail")
  private val emailCreateAssignableProperties: Set[String] = Set("mailboxIds", "messageId", "references", "inReplyTo",
    "from", "to", "cc", "bcc", "sender", "replyTo", "subject", "sentAt", "keywords", "receivedAt",
    "htmlBody", "textBody", "bodyValues", "specificHeaders", "attachments")
}

case class EmailSendCreationRequest(emailCreate: EmailCreationRequest,
                                    emailSubmissionSet: EmailSubmissionCreationRequest)

case class EmailSendCreationRequestRaw(emailCreate: JsObject,
                                       emailSubmissionSet: JsObject) {

  def validate(): Either[EmailSendCreationRequestInvalidException, EmailSendCreationRequestRaw] =
    validateEmailCreate()
      .flatMap(_ => validateEmailSubmissionSet())

  def validateEmailSubmissionSet(): Either[EmailSendCreationRequestInvalidException, EmailSendCreationRequestRaw] =
    emailSubmissionSet.keys.diff(emailSubmissionAssignableProperties) match {
      case unknownProperties if unknownProperties.nonEmpty =>
        Left(EmailSendCreationRequestInvalidException(SetError.invalidArguments(
          SetErrorDescription("Some unknown properties were specified"),
          Some(toProperties(unknownProperties.toSet)))))
      case _ => scala.Right(this)
    }

  def validateEmailCreate(): Either[EmailSendCreationRequestInvalidException, EmailSendCreationRequestRaw] =
    emailCreate.keys.diff(emailCreateAssignableProperties) match {
      case unknownProperties if unknownProperties.nonEmpty =>
        Left(EmailSendCreationRequestInvalidException(SetError.invalidArguments(
          SetErrorDescription("Some unknown properties were specified"),
          Some(toProperties(unknownProperties.toSet)))))
      case _ => scala.Right(this)
    }

  def toModel(emailSetSerializer: EmailSetSerializer): Either[EmailSendCreationRequestInvalidException, EmailSendCreationRequest] = {
    val maybeEmailCreationRequest: Either[EmailSendCreationRequestInvalidException, EmailCreationRequest] =
      emailSetSerializer.deserializeCreationRequest(emailCreate) match {
        case JsSuccess(value, _) => scala.Right(value)
        case JsError(errors) => Left(EmailSendCreationRequestInvalidException.parse(errors))
      }

    maybeEmailCreationRequest.flatMap(emailCreate =>
      EmailSendSerializer.deserializeEmailCreationRequest(emailSubmissionSet) match {
        case JsSuccess(value, _) => scala.Right(EmailSendCreationRequest(emailCreate, value))
        case JsError(errors) => Left(EmailSendCreationRequestInvalidException.parse(errors))
      })
  }
}

case class EmailSubmissionCreationRequest(identityId: Option[Id],
                                          envelope: Option[Envelope])

case class EmailSendId(value: Id)

case class EmailSendCreationResponse(emailSubmissionId: EmailSubmissionId,
                                     emailId: MessageId,
                                     blobId: Option[BlobId],
                                     threadId: ThreadId,
                                     size: Size)

trait EmailSetCreationResult

case class EmailSetCreationSuccess(clientId: EmailSendCreationId,
                                   response: EmailCreationResponse,
                                   originalMessage: Array[Byte]) extends EmailSetCreationResult

case class EmailSetCreationFailure(clientId: EmailSendCreationId, error: Throwable) extends EmailSetCreationResult


object EmailSendResults {
  def empty(): EmailSendResults = EmailSendResults(None, None, Map.empty)

  def created(emailSendCreationId: EmailSendCreationId, emailSendCreationResponse: EmailSendCreationResponse): EmailSendResults =
    EmailSendResults(Some(Map(emailSendCreationId -> emailSendCreationResponse)),
      None,
      Map(emailSendCreationId -> emailSendCreationResponse.emailId))

  def notCreated(emailSendCreationId: EmailSendCreationId, throwable: Throwable): EmailSendResults = {
    val setError: SetError = throwable match {
      case invalidException: EmailSendCreationRequestInvalidException =>invalidException.error
      case error: Throwable => SetError.serverFail(SetErrorDescription(error.getMessage))
    }
    EmailSendResults(None, Some(Map(emailSendCreationId -> setError)), Map.empty)
  }

  def merge(result1: EmailSendResults, result2: EmailSendResults): EmailSendResults = EmailSendResults(
    created = (result1.created ++ result2.created).reduceOption(_ ++ _),
    notCreated = (result1.notCreated ++ result2.notCreated).reduceOption(_ ++ _),
    creationIdResolver = result1.creationIdResolver ++ result2.creationIdResolver)
}


case class EmailSendResults(created: Option[Map[EmailSendCreationId, EmailSendCreationResponse]],
                            notCreated: Option[Map[EmailSendCreationId, SetError]],
                            creationIdResolver: Map[EmailSendCreationId, MessageId]) {
  def asResponse(accountId: AccountId, newState: UuidState): EmailSendResponse = EmailSendResponse(
    accountId = accountId,
    newState = newState,
    created = created,
    notCreated = notCreated)

  def resolveMessageId(creationId: EmailSendCreationId): Either[IllegalArgumentException, Option[MessageId]] =
    if (creationId.id.startsWith("#")) {
      val realId: String = creationId.id.substring(1)
      val validatedId: Either[IllegalArgumentException, EmailSendCreationId] = Id.validate(realId).map(id => EmailSendCreationId(id))
      validatedId
        .left.map(s => new IllegalArgumentException(s))
        .flatMap(id => retrieveMessageId(id)
          .map(id => scala.Right(Some(id))).getOrElse(scala.Right(None)))
    } else {
      Left(new IllegalArgumentException(s"${creationId.id} cannot be retrieved as storage for EmailSend is not yet implemented"))
    }

  private def retrieveMessageId(creationId: EmailSendCreationId): Option[MessageId] =
    created.getOrElse(Map.empty).
      filter(sentResult => sentResult._1.equals(creationId)).keys
      .headOption
      .flatMap(creationId => creationIdResolver.get(creationId))
}

case class EmailSendResponse(accountId: AccountId,
                             newState: UuidState,
                             created: Option[Map[EmailSendCreationId, EmailSendCreationResponse]],
                             notCreated: Option[Map[EmailSendCreationId, SetError]])

case class MimeMessageSourceImpl(name: String, message: Array[Byte]) extends MimeMessageSource {

  override def getSourceId: String = name

  override def getInputStream: InputStream = new ByteArrayInputStream(message)
}