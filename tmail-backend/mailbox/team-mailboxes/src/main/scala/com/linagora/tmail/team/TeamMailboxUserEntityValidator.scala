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
