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

import com.google.common.annotations.VisibleForTesting
import com.linagora.tmail.team.PropagateDeleteRightTeamMailboxListener.GROUP
import com.linagora.tmail.team.TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.events.EventListener.ReactiveGroupEventListener
import org.apache.james.events.{Event, Group}
import org.apache.james.mailbox.MailboxManager
import org.apache.james.mailbox.acl.ACLDiff
import org.apache.james.mailbox.events.MailboxEvents.MailboxACLUpdated
import org.apache.james.mailbox.model.MailboxACL.EntryKey
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.scala.publisher.{SFlux, SMono}

case class PropagateDeleteMailboxRightListenerGroup() extends Group {}

object PropagateDeleteRightTeamMailboxListener {
  val GROUP: PropagateDeleteMailboxRightListenerGroup = PropagateDeleteMailboxRightListenerGroup()
}

class PropagateDeleteRightTeamMailboxListener @Inject()(teamMailboxRepository: TeamMailboxRepository,
                                                        mailboxManager: MailboxManager) extends ReactiveGroupEventListener {

  private val logger = LoggerFactory.getLogger(classOf[PropagateDeleteRightTeamMailboxListener])

  override def getDefaultGroup: Group = GROUP

  override def isHandling(event: Event): Boolean = event.isInstanceOf[MailboxACLUpdated]

  override def reactiveEvent(event: Event): Publisher[Void] = {
    event match {
      case mailboxACLUpdated: MailboxACLUpdated if TEAM_MAILBOX_NAMESPACE.equals(mailboxACLUpdated.getMailboxPath.getNamespace) =>
        TeamMailbox.from(mailboxACLUpdated.getMailboxPath) match {
          case Some(teamMailbox) if !teamMailbox.defaultMailboxPaths.contains(mailboxACLUpdated.getMailboxPath) =>
            SFlux(teamMailboxRepository.listMembers(teamMailbox))
              .filter(member => needsToAddRight(mailboxACLUpdated.getAclDiff, member.username))
              .flatMap(teamMailboxMember => applyRightsCommand(teamMailboxMember.username, mailboxACLUpdated.getMailboxPath))
          case _ => SMono.empty
        }
      case _ => SMono.empty
    }
  }

  private def applyRightsCommand(member: Username, mailboxPath: MailboxPath): SMono[Void] =
    SMono(mailboxManager.applyRightsCommandReactive(
      mailboxPath,
      MailboxACL.command()
        .forUser(member)
        .rights(MailboxACL.Right.DeleteMailbox)
        .asAddition(),
      mailboxManager.createSystemSession(mailboxPath.getUser)))
      .onErrorResume(error => {
        logger.error(s"Failed to add `DeleteMailbox` right command for $member on $mailboxPath", error)
        SMono.empty
      })

  @VisibleForTesting
  def needsToAddRight(aclDiff: ACLDiff, member: Username): Boolean = {
    val entryKey: EntryKey = EntryKey.createUserEntryKey(member)
    val oldEntries: Option[MailboxACL.Rfc4314Rights] = Option(aclDiff.getOldACL.getEntries.get(entryKey))
    val newEntries: Option[MailboxACL.Rfc4314Rights] = Option(aclDiff.getNewACL.getEntries.get(entryKey))

    oldEntries.forall(_.isEmpty) && newEntries.isDefined &&
      newEntries.exists(rights => !rights.contains(MailboxACL.Right.DeleteMailbox))
  }
}
