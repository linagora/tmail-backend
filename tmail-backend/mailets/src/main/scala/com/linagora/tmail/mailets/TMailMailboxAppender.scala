package com.linagora.tmail.mailets

import com.linagora.tmail.team.{TeamMailbox, TeamMailboxRepository}
import org.apache.james.core.{Domain, Username}
import org.apache.james.mailbox.MessageManager.AppendResult
import org.apache.james.mailbox.model.ComposedMessageId
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.transport.mailets.delivery.MailboxAppenderImpl
import reactor.core.scala.publisher.SMono

import javax.mail.internet.MimeMessage

class TMailMailboxAppender(teamMailboxRepository: TeamMailboxRepository, mailboxManager: MailboxManager) extends MailboxAppenderImpl(mailboxManager) {
  override def append(mail: MimeMessage, user: Username, folder: String): ComposedMessageId =
    TeamMailbox.asTeamMailbox(user.asMailAddress()) match {
      case Some(teamMailbox) => appendTeamMailbox(mail, teamMailbox, user, folder)
      case _ => super.append(mail, user, folder)
    }

  def appendTeamMailbox(mail: MimeMessage, teamMailbox: TeamMailbox, user: Username, folder: String): ComposedMessageId =
    if (SMono.fromPublisher(teamMailboxRepository.exists(teamMailbox)).block()) {
      appendMessageToTeamMailbox(mail, teamMailbox).getId
    } else {
      super.append(mail, user, folder)
    }

  def appendMessageToTeamMailbox(mail: MimeMessage, teamMailbox: TeamMailbox): AppendResult =
    super.appendMessageToMailbox(mail, mailboxManager.createSystemSession(teamMailbox.owner), teamMailbox.inboxPath)
}
