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

package com.linagora.tmail.james.jmap.team.mailboxes

import com.linagora.tmail.team.TeamMailbox
import org.apache.james.jmap.mail.{DelegatedNamespace, MailboxNamespace, NamespaceFactory, PersonalNamespace}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MailboxPath

case class TeamMailboxNamespace(teamMailbox: TeamMailbox) extends MailboxNamespace {
  override def serialize(): String = s"TeamMailbox[${teamMailbox.asMailAddress.asString()}]"
}

class TMailNamespaceFactory extends NamespaceFactory {
  override def from(mailboxPath: MailboxPath, mailboxSession: MailboxSession): MailboxNamespace =
    if (mailboxPath.belongsTo(mailboxSession)) {
      PersonalNamespace()
    } else {
      TeamMailbox.from(mailboxPath)
        .map(TeamMailboxNamespace)
        .getOrElse(DelegatedNamespace(mailboxPath.getUser))
    }
}
