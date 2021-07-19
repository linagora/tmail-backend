package com.linagora.tmail.james.jmap.model

import cats.implicits.toTraverseOps
import org.apache.james.core.MailAddress
import org.apache.james.jmap.mail.{EmailSubmissionAddress, Envelope}
import org.apache.james.jmap.method.MessageMimeMessageSource
import org.apache.james.lifecycle.api.LifecycleUtil
import org.apache.james.mailbox.model.MessageResult
import org.apache.james.mime4j.dom.Message
import org.apache.james.server.core.MimeMessageWrapper

import javax.mail.Address
import javax.mail.Message.RecipientType
import javax.mail.internet.{InternetAddress, MimeMessage}
import scala.util.{Failure, Success, Try}

object EmailSubmissionHelper {
  def resolveEnvelope(mimeMessage: MimeMessage, maybeEnvelope: Option[Envelope]): Try[Envelope] =
    maybeEnvelope.map(Success(_)).getOrElse(extractEnvelope(mimeMessage))

  def extractEnvelope(mimeMessage: MimeMessage): Try[Envelope] = {
    val to: List[Address] = Option(mimeMessage.getRecipients(RecipientType.TO)).toList.flatten
    val cc: List[Address] = Option(mimeMessage.getRecipients(RecipientType.CC)).toList.flatten
    val bcc: List[Address] = Option(mimeMessage.getRecipients(RecipientType.BCC)).toList.flatten
    for {
      mailFrom <- Option(mimeMessage.getFrom).toList.flatten
        .headOption
        .map(_.asInstanceOf[InternetAddress].getAddress)
        .map(s => Try(new MailAddress(s)))
        .getOrElse(Failure(new IllegalArgumentException("Implicit envelope detection requires a from field")))
        .map(EmailSubmissionAddress)
      rcptTo <- (to ++ cc ++ bcc)
        .map(_.asInstanceOf[InternetAddress].getAddress)
        .map(s => Try(new MailAddress(s)))
        .sequence
    } yield {
      Envelope(mailFrom, rcptTo.map(EmailSubmissionAddress))
    }
  }


}
