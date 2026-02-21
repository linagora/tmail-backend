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
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.util.Try

class TMailCanSendFrom @Inject()(aliasReverseResolver: AliasReverseResolver,
                                 teamMailboxRepository: TeamMailboxRepository,
                                 @Named("mailboxmanager") mailboxManager: MailboxManager) extends CanSendFromImpl(aliasReverseResolver) {
  override def userCanSendFrom(connectedUser: Username, fromUser: Username): Boolean =
    super.userCanSendFrom(connectedUser, fromUser) || validTeamMailbox(connectedUser, fromUser)

  override def userCanSendFromReactive(connectedUser: Username, fromUser: Username): Publisher[lang.Boolean] =
    SFlux.merge(Seq(
      SMono.fromPublisher(super.userCanSendFromReactive(connectedUser, fromUser)).map(javaPrimitiveBoolean => lang.Boolean.valueOf(javaPrimitiveBoolean)),
      validTeamMailboxReactive(connectedUser, fromUser).map(boolean2Boolean)))
      .reduce((bool1: lang.Boolean, bool2: lang.Boolean) => bool1 || bool2)

  private def isExtraSender(user: Username, teamMailbox: TeamMailbox): Boolean = {
    val session = mailboxManager.createSystemSession(teamMailbox.admin)
    val userKey = MailboxACL.EntryKey.createUserEntryKey(user)
    Try(mailboxManager.listRights(teamMailbox.mailboxPath, session))
      .map(acl => Option(acl.getEntries.get(userKey)).exists(_.contains(Right.Post)))
      .getOrElse(false)
  }

  private def validTeamMailbox(connectedUser: Username, fromUser: Username): Boolean =
    TeamMailbox.asTeamMailbox(fromUser.asMailAddress()) match {
      case Some(teamMailbox) =>
        SFlux(teamMailboxRepository.listMembers(teamMailbox))
          .filter(teamMailboxMember => connectedUser.equals(teamMailboxMember.username))
          .hasElements
          .onErrorResume {
            case _: TeamMailboxNotFoundException => SMono.just(false)
            case e => SMono.error(e)
          }
          .block() || isExtraSender(connectedUser, teamMailbox)
      case None => false
    }

  private def isExtraSenderReactive(user: Username, teamMailbox: TeamMailbox): SMono[Boolean] = {
    val session = mailboxManager.createSystemSession(teamMailbox.admin)
    val userKey = MailboxACL.EntryKey.createUserEntryKey(user)
    SMono.fromPublisher(mailboxManager.listRightsReactive(teamMailbox.mailboxPath, session))
      .map(acl => Option(acl.getEntries.get(userKey)).exists(_.contains(Right.Post)))
      .onErrorResume(_ => SMono.just(false))
  }

  private def validTeamMailboxReactive(connectedUser: Username, fromUser: Username): SMono[Boolean] =
    TeamMailbox.asTeamMailbox(fromUser.asMailAddress()) match {
      case Some(teamMailbox) =>
        SFlux(teamMailboxRepository.listMembers(teamMailbox))
          .filter(teamMailboxMember => connectedUser.equals(teamMailboxMember.username))
          .hasElements
          .onErrorResume {
            case _: TeamMailboxNotFoundException => SMono.just(false)
            case e => SMono.error(e)
          }
          .flatMap(isMember => if (isMember) SMono.just(true) else isExtraSenderReactive(connectedUser, teamMailbox))
      case None => SMono.just(false)
    }

  override def allValidFromAddressesForUser(user: Username): Flux[MailAddress] = {
    val extraSenderAddresses: Flux[MailAddress] =
      if (user.getDomainPart.isPresent) {
        Flux.from(SFlux.fromPublisher(teamMailboxRepository.listTeamMailboxes(user.getDomainPart.get))
          .filterWhen(teamMailbox => isExtraSenderReactive(user, teamMailbox))
          .map(_.asMailAddress))
      } else {
        Flux.empty[MailAddress]()
      }
    Flux.merge(
      super.allValidFromAddressesForUser(user),
      Flux.from(teamMailboxRepository.listTeamMailboxes(user)).map(_.asMailAddress),
      extraSenderAddresses)
      .distinct()
  }
}
