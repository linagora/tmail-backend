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

import java.util.Locale

import cats.implicits.toTraverseOps
import jakarta.mail.Address
import jakarta.mail.Message.RecipientType
import jakarta.mail.internet.{AddressException, InternetAddress, MimeMessage, MimeUtility}
import org.apache.james.core.MailAddress
import org.apache.james.jmap.mail.{EmailSubmissionAddress, Envelope}
import org.apache.james.mime4j.field.address.LenientAddressParser
import org.apache.james.mime4j.stream.ParserCursor
import org.apache.james.mime4j.util.ContentUtil

import scala.jdk.CollectionConverters._
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
        .map(EmailSubmissionAddress(_))
      rcptTo <- (to ++ cc ++ bcc)
        .map(_.asInstanceOf[InternetAddress].getAddress)
        .map(s => Try(new MailAddress(s)))
        .sequence
    } yield {
      Envelope(mailFrom, rcptTo.map(EmailSubmissionAddress(_)))
    }
  }

  // The From field defaults to the Sender field, as for MimeMessage::getFrom.
  // Left holds a field that cannot be parsed, as is.
  def senderAddresses(mimeMessage: MimeMessage): List[(String, Either[String, String])] = {
    val sender: Option[String] = Option(mimeMessage.getHeader("Sender", ","))
    val from: Option[String] = Option(mimeMessage.getHeader("From", ",")).orElse(sender)
    sender.toList.flatMap(fieldAddresses).map("sender" -> _) ++
      from.toList.flatMap(fieldAddresses).map("from" -> _)
  }

  private def fieldAddresses(field: String): List[Either[String, String]] =
    try {
      addresses(field).map(Right(_))
    } catch {
      case _: AddressException => List(Left(field))
    }

  /**
   * Reads an address field (From, Sender...) for an authorization check.
   *
   * jakarta.mail ignores what follows the first mailbox ('<bob@domain.tld> alice@domain.tld' reads as
   * bob@domain.tld) and stops at comments ('bob@domain.tld(comment).evil' reads as bob@domain.tld) while
   * mime4j, used for instance by JMAP Email/get, reads alice@domain.tld and bob@domain.tld.evil.
   *
   * @return the addresses jakarta.mail reads strictly, followed by the mailboxes mime4j reads in addition,
   * so that a check covers what any of them displays.
   *
   * Same as org.apache.james.server.core.AddressFields::parse (apache/james-project#3357): use it instead
   * once TMail is on a James version that has it.
   */
  @throws[AddressException]
  def addresses(field: String): List[String] = {
    val unfolded: String = MimeUtility.unfold(field)
    val jakartaAddresses: List[InternetAddress] = InternetAddress.parseHeader(unfolded, true).toList
    val jakartaMailboxes: Set[String] = jakartaAddresses
      .flatMap(flatten)
      .map(_.getAddress.toLowerCase(Locale.US))
      .toSet

    jakartaAddresses.map(_.getAddress) ++
      mime4jMailboxes(unfolded)
        .filterNot(address => jakartaMailboxes.contains(address.toLowerCase(Locale.US)))
        .distinct
  }

  private def flatten(address: InternetAddress): List[InternetAddress] =
    if (address.isGroup) {
      Try(address.getGroup(false).toList).getOrElse(List())
    } else {
      List(address)
    }

  private def mime4jMailboxes(field: String): List[String] = {
    // LenientAddressParser::parseAddressList(CharSequence) stops after field.length() bytes of the encoded
    // field, leaving the end of a field holding non-ASCII characters unread
    val raw = ContentUtil.encode(field)
    Try(LenientAddressParser.DEFAULT.parseAddressList(raw, new ParserCursor(0, raw.length()))
      .flatten()
      .asScala
      .toList
      .map(_.getAddress))
      .getOrElse(List())
  }
}
