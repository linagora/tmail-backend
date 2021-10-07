package com.linagora.tmail.team

import com.linagora.tmail.team.TeamMailboxUserEntityValidator.TEAM_MAILBOX
import org.apache.james.UserEntityValidator
import org.apache.james.UserEntityValidator.{EntityType, ValidationFailure}
import org.apache.james.core.{Domain, Username}
import reactor.core.scala.publisher.SMono

import java.util
import java.util.Optional
import javax.inject.Inject
import scala.jdk.OptionConverters._

object TeamMailboxUserEntityValidator {
  val TEAM_MAILBOX: EntityType = new EntityType("team-mailbox")
}

class TeamMailboxUserEntityValidator @Inject()(teamMailboxRepository: TeamMailboxRepository) extends UserEntityValidator {
  override def canCreate(username: Username, ignoredTypes: util.Set[EntityType]): Optional[UserEntityValidator.ValidationFailure] =
    if (ignoredTypes.contains(TEAM_MAILBOX)) {
      Optional.empty
    } else {
      check(username).block()
        .toJava
    }

  private def check(username: Username): SMono[Option[UserEntityValidator.ValidationFailure]] =
    SMono.just(username.getDomainPart)
      .filter(domain => domain.isPresent)
      .flatMap(domain => checkByDomain(username, domain.get))

  private def checkByDomain(username: Username, domain: Domain): SMono[Option[UserEntityValidator.ValidationFailure]] =
    SMono.fromPublisher(teamMailboxRepository.exists(TeamMailbox.fromJava(domain, username.getLocalPart).get))
      .filter(tmbx => tmbx)
      .map(any => Some(new ValidationFailure("'" + username.asString + "' team-mailbox already exists")))
}
