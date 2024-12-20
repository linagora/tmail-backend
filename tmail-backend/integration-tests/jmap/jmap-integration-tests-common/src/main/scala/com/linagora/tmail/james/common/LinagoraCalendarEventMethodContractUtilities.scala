package com.linagora.tmail.james.common

import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.rfc8621.contract.Fixture.BOB
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.util.ClassLoaderUtils

import scala.util.Using

object LinagoraCalendarEventMethodContractUtilities {
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
