package com.linagora.tmail.mailets

import com.linagora.tmail.team.{TeamMailbox, TeamMailboxRepository}
import org.apache.james.core.MailAddress
import org.apache.mailet.Mail
import org.apache.mailet.base.GenericMatcher
import reactor.core.scala.publisher.{SFlux, SMono}

import java.util
import scala.jdk.CollectionConverters._

class IsTeamMailbox(teamMailboxRepository: TeamMailboxRepository) extends GenericMatcher {
  override def `match`(mail: Mail): util.Collection[MailAddress] =
    SFlux.fromIterable(mail.getRecipients.asScala)
      .filterWhen(address => isTeamMailbox(address))
      .collectSeq()
      .block()
      .asJavaCollection

  private def isTeamMailbox(address: MailAddress): SMono[Boolean] =
    TeamMailbox.asTeamMailbox(address)
      .map(teamMailbox => SMono(teamMailboxRepository.exists(teamMailbox)))
      .getOrElse(SMono.just(false))
}
