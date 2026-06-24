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

import java.lang

import jakarta.inject.{Inject, Named}
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.mailbox.MailboxManager
import org.apache.james.mailbox.model.MailboxACL
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.rrt.api.AliasReverseResolver
import org.apache.james.rrt.lib.CanSendFromImpl
import reactor.core.publisher.{Flux, Mono}
import reactor.core.scala.publisher.{SFlux, SMono}

class TMailCanSendFrom @Inject()(aliasReverseResolver: AliasReverseResolver,
                                 teamMailboxRepository: TeamMailboxRepository,
                                 @Named("mailboxmanager") mailboxManager: MailboxManager) extends CanSendFromImpl(aliasReverseResolver) {
  private def isExtraSenderReactive(user: Username, teamMailbox: TeamMailbox): SMono[Boolean] = {
    val session = mailboxManager.createSystemSession(teamMailbox.admin)
    val userKey = MailboxACL.EntryKey.createUserEntryKey(user)
    SMono.fromPublisher(mailboxManager.listRightsReactive(teamMailbox.mailboxPath, session))
      .map(acl => Option(acl.getEntries.get(userKey)).exists(_.contains(Right.Post)))
      .onErrorResume(_ => SMono.just(false))
  }

  override def allValidFromAddressesForUser(user: Username): Flux[MailAddress] = {
    val extraSenderAddresses: Flux[Username] =
      if (user.getDomainPart.isPresent) {
        Flux.from(SFlux.fromPublisher(teamMailboxRepository.listTeamMailboxes(user.getDomainPart.get))
          .filterWhen(teamMailbox => isExtraSenderReactive(user, teamMailbox))
          .map(_.asMailAddress)
          .map(Username.fromMailAddress(_)))
      } else {
        Flux.empty[Username]()
      }
    Flux.merge(
      // The username itself.
      Mono.just(user),
      // All team mailboxes of which the user is member (has BASIC_TEAM_MAILBOX_RIGHTS).
      Flux.from(teamMailboxRepository.listTeamMailboxes(user)).map(_.asMailAddress).map(Username.fromMailAddress(_)),
      // All team mailboxes on the same domain of which the user is an extra sender (has Post right).
      extraSenderAddresses
    )
    .distinct()
    .flatMap(super.allValidFromAddressesForUser(_))
    .distinct()
  }
}
