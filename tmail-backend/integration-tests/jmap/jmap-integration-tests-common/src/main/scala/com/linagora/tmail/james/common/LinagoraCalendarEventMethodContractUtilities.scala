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
