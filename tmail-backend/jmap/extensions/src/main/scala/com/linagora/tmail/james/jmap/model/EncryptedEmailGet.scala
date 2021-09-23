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
                                     list: List[EncryptedEmailFastView],
                                     notFound: EmailNotFound)

object EncryptedEmailFastViewResults {
  def notFound(unparsedEmailId: UnparsedEmailId): EncryptedEmailFastViewResults =
    EncryptedEmailFastViewResults(List(), EmailNotFound(Set(unparsedEmailId)))

  def notFound(messageId: MessageId): EncryptedEmailFastViewResults =
    EncryptedEmailFastViewResults(List(), EmailNotFound(Set(Email.asUnparsed(messageId).get)))

  def list(fastView: EncryptedEmailFastView): EncryptedEmailFastViewResults =
    EncryptedEmailFastViewResults(List(fastView), EmailNotFound(Set()))

  def empty(): EncryptedEmailFastViewResults =
    EncryptedEmailFastViewResults(List(), EmailNotFound(Set()))

  def merge(result1: EncryptedEmailFastViewResults, result2: EncryptedEmailFastViewResults): EncryptedEmailFastViewResults =
    EncryptedEmailFastViewResults(
      list = result1.list ++ result2.list,
      notFound = EmailNotFound(result1.notFound.value ++ result2.notFound.value))
}

case class EncryptedEmailFastViewResults(list: List[EncryptedEmailFastView],
                                         notFound: EmailNotFound)

object EmailIdHelper {
  def parser(ids: List[UnparsedEmailId], messageIdFactory: MessageId.Factory): List[Either[(UnparsedEmailId, IllegalArgumentException), MessageId]] =
    ids.map(id =>
      Try(messageIdFactory.fromString(id.id.value))
        .toEither
        .left.map(e => id -> new IllegalArgumentException(e)))
}

case class EncryptedEmailDetailedResponse(accountId: AccountId,
                                     state: UuidState,
                                     list: List[EncryptedEmailDetailedView],
                                     notFound: EmailNotFound)

object EncryptedEmailDetailedViewResults {
  def notFound(unparsedEmailId: UnparsedEmailId): EncryptedEmailDetailedViewResults =
    EncryptedEmailDetailedViewResults(List(), EmailNotFound(Set(unparsedEmailId)))

  def notFound(messageId: MessageId): EncryptedEmailDetailedViewResults =
    EncryptedEmailDetailedViewResults(List(), EmailNotFound(Set(Email.asUnparsed(messageId).get)))

  def list(detailedView: EncryptedEmailDetailedView): EncryptedEmailDetailedViewResults =
    EncryptedEmailDetailedViewResults(List(detailedView), EmailNotFound(Set()))

  def empty(): EncryptedEmailDetailedViewResults =
    EncryptedEmailDetailedViewResults(List(), EmailNotFound(Set()))

  def merge(result1: EncryptedEmailDetailedViewResults, result2: EncryptedEmailDetailedViewResults): EncryptedEmailDetailedViewResults =
    EncryptedEmailDetailedViewResults(
      list = result1.list ++ result2.list,
      notFound = EmailNotFound(result1.notFound.value ++ result2.notFound.value))
}

case class EncryptedEmailDetailedViewResults(list: List[EncryptedEmailDetailedView],
                                             notFound: EmailNotFound)
