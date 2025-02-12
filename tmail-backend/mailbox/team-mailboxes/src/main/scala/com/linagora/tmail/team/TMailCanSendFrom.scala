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

import jakarta.inject.Inject
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.rrt.api.AliasReverseResolver
import org.apache.james.rrt.lib.CanSendFromImpl
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.scala.publisher.{SFlux, SMono}

class TMailCanSendFrom @Inject()(aliasReverseResolver: AliasReverseResolver, teamMailboxRepository: TeamMailboxRepository) extends CanSendFromImpl(aliasReverseResolver) {
  override def userCanSendFrom(connectedUser: Username, fromUser: Username): Boolean =
    super.userCanSendFrom(connectedUser, fromUser) || validTeamMailbox(connectedUser, fromUser)

  override def userCanSendFromReactive(connectedUser: Username, fromUser: Username): Publisher[lang.Boolean] =
    SFlux.merge(Seq(
      SMono.fromPublisher(super.userCanSendFromReactive(connectedUser, fromUser)).map(javaPrimitiveBoolean => lang.Boolean.valueOf(javaPrimitiveBoolean)),
      validTeamMailboxReactive(connectedUser, fromUser).map(boolean2Boolean)))
      .reduce((bool1: lang.Boolean, bool2: lang.Boolean) => bool1 || bool2)

  private def validTeamMailbox(connectedUser: Username, fromUser: Username): Boolean =
    TeamMailbox.asTeamMailbox(fromUser.asMailAddress()) match {
      case Some(teamMailbox) => SFlux(teamMailboxRepository.listMembers(teamMailbox))
        .filter(teamMailboxMember => connectedUser.equals(teamMailboxMember.username))
        .hasElements
        .onErrorResume {
          case _: TeamMailboxNotFoundException => SMono.just(false)
          case e => SMono.error(e)
        }
        .block()
      case None => false
    }

  private def validTeamMailboxReactive(connectedUser: Username, fromUser: Username): SMono[Boolean] =
    TeamMailbox.asTeamMailbox(fromUser.asMailAddress()) match {
      case Some(teamMailbox) => SFlux(teamMailboxRepository.listMembers(teamMailbox))
        .filter(teamMailboxMember => connectedUser.equals(teamMailboxMember.username))
        .hasElements
        .onErrorResume {
          case _: TeamMailboxNotFoundException => SMono.just(false)
          case e => SMono.error(e)
        }
      case None => SMono.just(false)
    }

  override def allValidFromAddressesForUser(user: Username): Flux[MailAddress] =
    Flux.merge(super.allValidFromAddressesForUser(user),
      Flux.from(teamMailboxRepository.listTeamMailboxes(user))
        .map(_.asMailAddress))
}
