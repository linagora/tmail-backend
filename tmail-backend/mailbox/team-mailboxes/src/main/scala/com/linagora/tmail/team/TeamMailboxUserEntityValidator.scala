package com.linagora.tmail.team

import java.util
import java.util.Optional

import com.linagora.tmail.team.TeamMailboxUserEntityValidator.TEAM_MAILBOX
import jakarta.inject.Inject
import org.apache.james.UserEntityValidator
import org.apache.james.UserEntityValidator.{EntityType, ValidationFailure}
import org.apache.james.core.{Domain, Username}
import reactor.core.scala.publisher.SMono

import scala.jdk.OptionConverters._

object TeamMailboxUserEntityValidator {
  val TEAM_MAILBOX: EntityType = new EntityType("team-mailbox")
}

class TeamMailboxUserEntityValidator @Inject()(teamMailboxRepository: TeamMailboxRepository) extends UserEntityValidator {
  override def canCreate(username: Username, ignoredTypes: util.Set[EntityType]): Optional[UserEntityValidator.ValidationFailure] =
    if (ignoredTypes.contains(TEAM_MAILBOX)) {
      Optional.empty
    } else {
      check(username)
        .toJava
    }

  private def check(username: Username): Option[UserEntityValidator.ValidationFailure] =
    username.getDomainPart
      .toScala
      .flatMap(domain => checkByDomain(username, domain))

  private def checkByDomain(username: Username, domain: Domain): Option[UserEntityValidator.ValidationFailure] =
    TeamMailbox.fromJava(domain, username.getLocalPart)
      .flatMap(teamMailbox => SMono.fromPublisher(teamMailboxRepository.exists(teamMailbox))
        .filter(b => b)
        .map(_ => new ValidationFailure(s"'${username.asString}' team-mailbox already exists"))
        .blockOption())
}
