package com.linagora.tmail.mailets

import com.linagora.tmail.mailets.TmailLocalDelivery.LOCAL_DELIVERED_MAILS_METRIC_NAME
import com.linagora.tmail.team.TeamMailboxRepository
import org.apache.james.mailbox.MailboxManager
import org.apache.james.mailbox.model.MailboxConstants
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.transport.mailets.delivery.{MailDispatcher, SimpleMailStore}
import org.apache.james.user.api.UsersRepository
import org.apache.mailet.Mail
import org.apache.mailet.base.GenericMailet

import javax.inject.{Inject, Named}

object TmailLocalDelivery {
  private val LOCAL_DELIVERED_MAILS_METRIC_NAME: String = "tmailLocalDeliveredMails"
}

class TmailLocalDelivery @Inject()(usersRepository: UsersRepository,
                                   @Named("mailboxmanager") mailboxManager: MailboxManager,
                                   teamMailboxRepository: TeamMailboxRepository,
                                   metricFactory: MetricFactory) extends GenericMailet {
  private var mailDispatcher: MailDispatcher = _

  override def service(mail: Mail): Unit = mailDispatcher.dispatch(mail)

  override def getMailetInfo(): String = "TMail local delivery mailet"

  override def init(): Unit = {
    mailDispatcher = MailDispatcher.builder
      .mailStore(SimpleMailStore.builder
        .mailboxAppender(new TMailMailboxAppender(teamMailboxRepository, mailboxManager))
        .usersRepository(usersRepository)
        .folder(MailboxConstants.INBOX)
        .metric(metricFactory.generate(LOCAL_DELIVERED_MAILS_METRIC_NAME))
        .build)
      .consume(getInitParameter("consume", true))
      .mailetContext(getMailetContext)
      .build
  }
}
