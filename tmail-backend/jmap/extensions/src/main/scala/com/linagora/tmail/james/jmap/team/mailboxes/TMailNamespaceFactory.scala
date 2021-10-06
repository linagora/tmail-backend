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
