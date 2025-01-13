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

package com.linagora.tmail.mailets

import java.util

import com.linagora.tmail.team.{TeamMailbox, TeamMailboxRepository}
import org.apache.james.core.MailAddress
import org.apache.mailet.Mail
import org.apache.mailet.base.GenericMatcher
import reactor.core.scala.publisher.{SFlux, SMono}

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
