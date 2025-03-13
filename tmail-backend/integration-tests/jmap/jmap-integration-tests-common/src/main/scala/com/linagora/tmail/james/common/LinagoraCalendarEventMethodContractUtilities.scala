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

package com.linagora.tmail.james.common

import java.io.{InputStreamReader, StringWriter, Writer}
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.{Base64, Optional, UUID}

import com.samskivert.mustache.{Mustache, Template}
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.rfc8621.contract.Fixture.BOB
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.SearchQuery.Sort.{Order, SortClause}
import org.apache.james.mailbox.model.{MailboxPath, MessageId, MultimailboxesSearchQuery, SearchQuery}
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.modules.protocols.SmtpGuiceProbe
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.SMTPMessageSender
import org.awaitility.Awaitility
import org.awaitility.core.ConditionFactory

import scala.util.Using

case class User(name: String, email: String, password: String) {
  lazy val username: Username = Username.of(email);
  lazy val accountId: String = AccountId.from(username).right.get.id.value
}

case class CalendarUsers(userOne: User, userTwo: User, userThree: User, userFour: User)

case class EmailData(sender: User, receiver: User, mimeMessageId: String)

object EmailData {
  def base64Encode: Mustache.Lambda = (frag: Template#Fragment, out: Writer) => {
    val writer = new StringWriter
    frag.execute(writer)
    val encoded = Base64.getEncoder.encodeToString(writer.toString.getBytes(StandardCharsets.UTF_8))
    out.write(encoded)
  }
}

object LinagoraCalendarEventMethodContractUtilities {
  private lazy val CALMLY_AWAIT: ConditionFactory = Awaitility.`with`
    .pollInterval(org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS)
    .and.`with`.pollDelay(org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS)
    .await

  def sendDynamicInvitationEmailAndGetIcsBlobIds(server: GuiceJamesServer, invitationEmailTemplate: String,
                                                 sender: User, receiver: User, icsPartIds: (String, String)): (String, String) =
    _sendDynamicInvitationEmailAndGetIcsBlobIds(server, invitationEmailTemplate, sender, receiver, icsPartIds._1, icsPartIds._2) match {
      case Seq(a, b) => (a, b)
    }

  def sendDynamicInvitationEmailAndGetIcsBlobIds(server: GuiceJamesServer, invitationEmailTemplate: String,
                                                 sender: User, receiver: User, icsPartIds: (String, String, String)): (String, String, String) =
    _sendDynamicInvitationEmailAndGetIcsBlobIds(server, invitationEmailTemplate, sender, receiver, icsPartIds._1, icsPartIds._2, icsPartIds._3) match {
      case Seq(a, b, c) => (a, b, c)
    }

  def sendDynamicInvitationEmailAndGetIcsBlobIds(server: GuiceJamesServer, invitationEmailTemplate: String, sender: User, receiver: User, icsPartId: String): String =
    _sendDynamicInvitationEmailAndGetIcsBlobIds(server, invitationEmailTemplate, sender, receiver, icsPartId) match {
      case Seq(a) => (a)
    }

  private def _sendDynamicInvitationEmailAndGetIcsBlobIds(server: GuiceJamesServer, invitationEmailTemplate: String,
                                                          sender: User, receiver: User, icsPartIds: String*): Seq[String] = {
    val templateAsString = ClassLoaderUtils.getSystemResourceAsString(invitationEmailTemplate)

    val emailTemplate = Mustache.compiler
      .withLoader((name: String) => new InputStreamReader(ClassLoaderUtils.getSystemResourceAsSharedStream("template/" + name)))
      .compile(templateAsString)

    val mimeMessageId = UUID.randomUUID().toString
    val mail = emailTemplate.execute(EmailData(sender, receiver, mimeMessageId))

    new SMTPMessageSender(sender.username.getDomainPart.get().asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(sender.username.asString(), sender.password)
      .sendMessageWithHeaders(sender.username.asString(), receiver.username.asString(), mail)

    var maybeMessageId: Optional[MessageId] = Optional.empty()
    CALMLY_AWAIT.atMost(5, TimeUnit.SECONDS)
      .dontCatchUncaughtExceptions()
      .until(() => {
        maybeMessageId = searchReceiverInboxForNewMessages(server, receiver, mimeMessageId)
        maybeMessageId.isPresent
      })

    icsPartIds.map(partId => s"${maybeMessageId.get().serialize()}_$partId")
  }

  private def searchReceiverInboxForNewMessages(server: GuiceJamesServer, receiver: User, mimeMessageId: String): Optional[MessageId] =
    server.getProbe(classOf[MailboxProbeImpl])
      .searchMessage(
        MultimailboxesSearchQuery.from(
          SearchQuery.of(SearchQuery.mimeMessageID(mimeMessageId))).build,
        receiver.username.asString(), 1).stream().findFirst()

  private def _sendInvitationEmailToBobAndGetIcsBlobIds(server: GuiceJamesServer, invitationEml: String,
                                                icsPartIds: String*): Seq[String] = {

    Using(ClassLoaderUtils.getSystemResourceAsSharedStream(invitationEml))(stream => {
      val appendResult = server.getProbe(classOf[MailboxProbeImpl])
        .appendMessageAndGetAppendResult(
          BOB.asString(),
          MailboxPath.inbox(BOB),
          AppendCommand.from(stream))

      icsPartIds.map(partId => s"${appendResult.getId.getMessageId.serialize()}_$partId")
    }).get
  }

  def sendInvitationEmailToBobAndGetIcsBlobIds(server: GuiceJamesServer, invitationEml: String,
                                               icsPartId: String): String = {

    _sendInvitationEmailToBobAndGetIcsBlobIds(server, invitationEml, icsPartId) match {
      case Seq(a) => (a)
    }
  }

  def sendInvitationEmailToBobAndGetIcsBlobIds(server: GuiceJamesServer, invitationEml: String,
                                               icsPartIds: (String, String)): (String, String) = {

    _sendInvitationEmailToBobAndGetIcsBlobIds(server, invitationEml, icsPartIds._1, icsPartIds._2) match {
      case Seq(a, b) => (a, b)
    }
  }

  def sendInvitationEmailToBobAndGetIcsBlobIds(server: GuiceJamesServer, invitationEml: String,
                                               icsPartIds: (String, String, String)): (String, String, String) = {

    _sendInvitationEmailToBobAndGetIcsBlobIds(server, invitationEml, icsPartIds._1, icsPartIds._2, icsPartIds._3) match {
      case Seq(a, b, c) => (a, b, c)
    }
  }
}
