package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.encrypted.{EncryptedEmailDetailedView, EncryptedEmailFastView}
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.jmap.mail.{Email, EmailIds, EmailNotFound, RequestTooLargeException, UnparsedEmailId}
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.mailbox.model.MessageId

import scala.util.Try

object EncryptedEmailGetRequest {
  val MAXIMUM_NUMBER_OF_EMAIL_IDS: Int = 500
}

case class EncryptedEmailGetRequest(accountId: AccountId,
                                    ids: EmailIds) extends WithAccountId {

  import EncryptedEmailGetRequest._

  def validate: Either[RequestTooLargeException, EncryptedEmailGetRequest] =
    if (ids.value.length > MAXIMUM_NUMBER_OF_EMAIL_IDS) {
      Left(RequestTooLargeException("The number of ids requested by the client exceeds the maximum number the server is willing to process in a single method call"))
    } else {
      scala.Right(this)
    }
}

case class EncryptedEmailGetResponse(accountId: AccountId,
                                     state: UuidState,
                                     list: Option[Map[MessageId, EncryptedEmailFastView]],
                                     notFound: Option[EmailNotFound])

object EncryptedEmailFastViewResults {
  def notFound(unparsedEmailId: UnparsedEmailId): EncryptedEmailFastViewResults =
    EncryptedEmailFastViewResults(None, Some(EmailNotFound(Set(unparsedEmailId))))

  def notFound(messageId: MessageId): EncryptedEmailFastViewResults =
    EncryptedEmailFastViewResults(None, Some(EmailNotFound(Set(Email.asUnparsed(messageId).get))))

  def list(messageId: MessageId, fastView: EncryptedEmailFastView): EncryptedEmailFastViewResults =
    EncryptedEmailFastViewResults(Some(Map(messageId -> fastView)), None)

  def empty(): EncryptedEmailFastViewResults =
    EncryptedEmailFastViewResults(None, None)

  def merge(result1: EncryptedEmailFastViewResults, result2: EncryptedEmailFastViewResults): EncryptedEmailFastViewResults =
    EncryptedEmailFastViewResults(
      list = (result1.list ++ result2.list).reduceOption(_ ++ _),
      notFound = (result1.notFound ++ result2.notFound).reduceOption((notFound1, notFound2) => notFound1.merge(notFound2)))
}

case class EncryptedEmailFastViewResults(list: Option[Map[MessageId, EncryptedEmailFastView]],
                                         notFound: Option[EmailNotFound])

object EmailIdHelper {
  def parser(ids: List[UnparsedEmailId], messageIdFactory: MessageId.Factory): List[Either[(UnparsedEmailId, IllegalArgumentException), MessageId]] =
    ids.map(id =>
      Try(messageIdFactory.fromString(id.id.value))
        .toEither
        .left.map(e => id -> new IllegalArgumentException(e)))
}

case class EncryptedEmailDetailedResponse(accountId: AccountId,
                                     state: UuidState,
                                     list: Option[Map[MessageId, EncryptedEmailDetailedView]],
                                     notFound: Option[EmailNotFound])

object EncryptedEmailDetailedViewResults {
  def notFound(unparsedEmailId: UnparsedEmailId): EncryptedEmailDetailedViewResults =
    EncryptedEmailDetailedViewResults(None, Some(EmailNotFound(Set(unparsedEmailId))))

  def notFound(messageId: MessageId): EncryptedEmailDetailedViewResults =
    EncryptedEmailDetailedViewResults(None, Some(EmailNotFound(Set(Email.asUnparsed(messageId).get))))

  def list(messageId: MessageId, detailedView: EncryptedEmailDetailedView): EncryptedEmailDetailedViewResults =
    EncryptedEmailDetailedViewResults(Some(Map(messageId -> detailedView)), None)

  def empty(): EncryptedEmailDetailedViewResults =
    EncryptedEmailDetailedViewResults(None, None)

  def merge(result1: EncryptedEmailDetailedViewResults, result2: EncryptedEmailDetailedViewResults): EncryptedEmailDetailedViewResults =
    EncryptedEmailDetailedViewResults(
      list = (result1.list ++ result2.list).reduceOption(_ ++ _),
      notFound = (result1.notFound ++ result2.notFound).reduceOption((notFound1, notFound2) => notFound1.merge(notFound2)))

}

case class EncryptedEmailDetailedViewResults(list: Option[Map[MessageId, EncryptedEmailDetailedView]],
                                             notFound: Option[EmailNotFound])
