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
import java.util.{Base64, Optional}

import com.samskivert.mustache.{Mustache, Template}
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.rfc8621.contract.Fixture.BOB
import org.apache.james.mailbox.MessageManager.AppendCommand
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

case class EventInvitation(sender: User, receiver: User, joker: User)

object EventInvitation {
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

  def _sendDynamicInvitationEmailAndGetIcsBlobIds(server: GuiceJamesServer, invitationEmailTemplate: String,
                                                  eventInvitation: EventInvitation, icsPartIds: String*): Seq[String] = {

    def searchReceiverInboxForNewMessages(): Optional[MessageId] =
      server.getProbe(classOf[MailboxProbeImpl])
        .searchMessage(
          MultimailboxesSearchQuery.from(
            SearchQuery.of(SearchQuery.all)).build, eventInvitation.receiver.username.asString(), 1).stream().findAny()

    val templateAsString = ClassLoaderUtils.getSystemResourceAsString(invitationEmailTemplate)

    val emailTemplate = Mustache.compiler
      .withLoader((name: String) => new InputStreamReader(ClassLoaderUtils.getSystemResourceAsSharedStream("template/" + name)))
      .compile(templateAsString)

    val mail = emailTemplate.execute(eventInvitation)

    println("MAIL: " + mail)

    new SMTPMessageSender(eventInvitation.sender.username.getDomainPart.get().asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(eventInvitation.sender.username.asString(), eventInvitation.sender.password)
      .sendMessageWithHeaders(eventInvitation.sender.username.asString(), eventInvitation.receiver.username.asString(), mail)

    CALMLY_AWAIT.atMost(5, TimeUnit.SECONDS)
      .dontCatchUncaughtExceptions()
      .until(() => searchReceiverInboxForNewMessages().isPresent)

    val messageId = searchReceiverInboxForNewMessages().get()

    icsPartIds.map(partId => s"${messageId.serialize()}_$partId")
  }

  def sendDynamicInvitationEmailAndGetIcsBlobIds(server: GuiceJamesServer, invitationEmailTemplate: String, eventInvitation: EventInvitation, icsPartId: String): String =

    _sendDynamicInvitationEmailAndGetIcsBlobIds(server, invitationEmailTemplate, eventInvitation, icsPartId) match {
      case Seq(a) => (a)
    }

  def sendDynamicInvitationEmailAndGetIcsBlobIds(server: GuiceJamesServer, invitationEmailTemplate: String,
                                                 eventInvitation: EventInvitation, icsPartIds: (String, String)): (String, String) =

    _sendDynamicInvitationEmailAndGetIcsBlobIds(server, invitationEmailTemplate, eventInvitation, icsPartIds._1, icsPartIds._2) match {
      case Seq(a, b) => (a, b)
    }

  def sendDynamicInvitationEmailAndGetIcsBlobIds(server: GuiceJamesServer, invitationEmailTemplate: String,
                                                 eventInvitation: EventInvitation, icsPartIds: (String, String, String)): (String, String, String) =

    _sendDynamicInvitationEmailAndGetIcsBlobIds(server, invitationEmailTemplate, eventInvitation, icsPartIds._1, icsPartIds._2, icsPartIds._3) match {
      case Seq(a, b, c) => (a, b, c)
    }

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
