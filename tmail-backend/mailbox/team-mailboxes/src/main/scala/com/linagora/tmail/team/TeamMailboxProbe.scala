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

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.mailbox.MailboxManager
import org.apache.james.mailbox.model.MailboxACL
import org.apache.james.mailbox.model.MailboxACL.Right
import org.apache.james.utils.GuiceProbe
import reactor.core.scala.publisher.{SFlux, SMono}

class TeamMailboxProbe @Inject()(teamMailboxRepository: TeamMailboxRepository,
                                 mailboxManager: MailboxManager) extends GuiceProbe {
  def create(teamMailbox: TeamMailbox): TeamMailboxProbe = {
    SMono(teamMailboxRepository.createTeamMailbox(teamMailbox)).block()
    this
  }

  def addMember(teamMailbox: TeamMailbox, username: Username): TeamMailboxProbe = {
    SMono(teamMailboxRepository.addMember(teamMailbox, TeamMailboxMember.asMember(username))).block()
    this
  }

  def addManager(teamMailbox: TeamMailbox, username: Username): TeamMailboxProbe = {
    SMono(teamMailboxRepository.addMember(teamMailbox, TeamMailboxMember.asManager(username))).block()
    this
  }

  def removeMember(teamMailbox: TeamMailbox, username: Username): TeamMailboxProbe = {
    SMono(teamMailboxRepository.removeMember(teamMailbox, username)).block()
    this
  }

  def listMembers(teamMailbox: TeamMailbox): Seq[Username] =
    SFlux(teamMailboxRepository.listMembers(teamMailbox))
      .map(_.username)
      .collectSeq()
      .block()

  def getMembers(teamMailbox: TeamMailbox): Seq[TeamMailboxMember] =
    SFlux(teamMailboxRepository.listMembers(teamMailbox))
      .collectSeq()
      .block()

  def addExtraSender(teamMailbox: TeamMailbox, username: Username): TeamMailboxProbe = {
    val session = mailboxManager.createSystemSession(teamMailbox.admin)
    mailboxManager.applyRightsCommand(
      teamMailbox.mailboxPath,
      MailboxACL.command().forUser(username).rights(Right.Post).asAddition(),
      session)
    this
  }

  def removeExtraSender(teamMailbox: TeamMailbox, username: Username): TeamMailboxProbe = {
    val session = mailboxManager.createSystemSession(teamMailbox.admin)
    mailboxManager.applyRightsCommand(
      teamMailbox.mailboxPath,
      MailboxACL.command().forUser(username).rights(Right.Post).asRemoval(),
      session)
    this
  }
}
