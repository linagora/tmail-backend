package com.linagora.tmail.mailets

import java.util.Optional

import com.google.common.collect.ImmutableMap
import com.linagora.tmail.mailets.TmailLocalDelivery.LOCAL_DELIVERED_MAILS_METRIC_NAME
import com.linagora.tmail.team.TeamMailboxRepository
import jakarta.inject.{Inject, Named}
import org.apache.commons.lang3.StringUtils
import org.apache.james.mailbox.MailboxManager
import org.apache.james.mailbox.model.MailboxConstants
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.transport.mailets.delivery.{MailDispatcher, SimpleMailStore}
import org.apache.james.user.api.UsersRepository
import org.apache.james.util.AuditTrail
import org.apache.mailet.Mail
import org.apache.mailet.base.{GenericMailet, MailetUtil}

object TmailLocalDelivery {
  private val LOCAL_DELIVERED_MAILS_METRIC_NAME: String = "tmailLocalDeliveredMails"
}

class TmailLocalDelivery @Inject()(usersRepository: UsersRepository,
                                   @Named("mailboxmanager") mailboxManager: MailboxManager,
                                   teamMailboxRepository: TeamMailboxRepository,
                                   metricFactory: MetricFactory) extends GenericMailet {
  private var mailDispatcher: MailDispatcher = _

  override def service(mail: Mail): Unit = {
    mailDispatcher.dispatch(mail)

    AuditTrail.entry
      .protocol("mailetcontainer")
      .action("TmailLocalDelivery")
      .parameters(() => ImmutableMap.of(
        "mailId", mail.getName,
        "mimeMessageId", Option(mail.getMessage).flatMap(message => Option(message.getMessageID)).getOrElse(""),
        "sender", mail.getMaybeSender.asString,
        "recipients", StringUtils.join(mail.getRecipients)))
      .log("Local delivery mail dispatched")
  }

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
      .retries(MailetUtil.getInitParameterAsInteger(getInitParameter("retries"), Optional.of(MailDispatcher.RETRIES)))
      .mailetContext(getMailetContext)
      .usersRepository(usersRepository)
      .build
  }
}
