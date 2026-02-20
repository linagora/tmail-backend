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

import com.linagora.tmail.team.TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.events.EventListener.ReactiveGroupEventListener
import org.apache.james.events.{Event, Group}
import org.apache.james.mailbox.{MailboxManager, SubscriptionManager}
import org.apache.james.mailbox.events.MailboxEvents.{MailboxACLUpdated, MailboxAdded}
import org.apache.james.mailbox.model.MailboxACL.NameType
import org.apache.james.mailbox.model.MailboxPath
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

case class TeamMailboxSubscriptionListenerGroup() extends Group {}

object TeamMailboxSubscriptionListener {
  val GROUP: TeamMailboxSubscriptionListenerGroup = TeamMailboxSubscriptionListenerGroup()
}

class TeamMailboxSubscriptionListener @Inject()(teamMailboxRepository: TeamMailboxRepository,
                                                mailboxManager: MailboxManager,
                                                subscriptionManager: SubscriptionManager) extends ReactiveGroupEventListener {

  private val logger = LoggerFactory.getLogger(classOf[TeamMailboxSubscriptionListener])

  override def getDefaultGroup: Group = TeamMailboxSubscriptionListener.GROUP

  override def isHandling(event: Event): Boolean =
    event.isInstanceOf[MailboxAdded] || event.isInstanceOf[MailboxACLUpdated]

  override def reactiveEvent(event: Event): Publisher[Void] = event match {
    case mailboxAdded: MailboxAdded if TEAM_MAILBOX_NAMESPACE.equals(mailboxAdded.getMailboxPath.getNamespace) =>
      handleMailboxAdded(mailboxAdded)
    case aclUpdated: MailboxACLUpdated if TEAM_MAILBOX_NAMESPACE.equals(aclUpdated.getMailboxPath.getNamespace) =>
      handleACLUpdated(aclUpdated)
    case _ => SMono.empty
  }

  private def handleMailboxAdded(event: MailboxAdded): Publisher[Void] =
    TeamMailbox.from(event.getMailboxPath) match {
      case Some(teamMailbox) =>
        val systemUsernames = Set(teamMailbox.owner.asString(), teamMailbox.admin.asString(), teamMailbox.self.asString())
        val ownerSession = mailboxManager.createSystemSession(teamMailbox.owner)

        SFlux(teamMailboxRepository.listMembers(teamMailbox))
          .map(_.username)
          .collectSeq()
          .flatMapMany(members => {
            val memberUsernames = members.map(_.asString()).toSet

            val memberSubscriptions = SFlux.fromIterable(members)
              .flatMap(username => subscribeUser(username, event.getMailboxPath))

            val extraAclSubscriptions = SMono(mailboxManager.listRightsReactive(event.getMailboxPath, ownerSession))
              .flatMapMany(acl => SFlux.fromIterable(acl.getEntries.entrySet().asScala))
              .filter(e => !e.getKey.isNegative)
              .filter(e => NameType.user.equals(e.getKey.getNameType))
              .filter(e => !systemUsernames.contains(e.getKey.getName))
              .filter(e => !memberUsernames.contains(e.getKey.getName))
              .map(e => Username.of(e.getKey.getName))
              .flatMap(username => subscribeUser(username, event.getMailboxPath))

            memberSubscriptions.mergeWith(extraAclSubscriptions)
          })
          .`then`()
      case None => SMono.empty
    }

  private def handleACLUpdated(event: MailboxACLUpdated): Publisher[Void] =
    TeamMailbox.from(event.getMailboxPath) match {
      case Some(teamMailbox) =>
        val systemUsernames = Set(teamMailbox.owner.asString(), teamMailbox.admin.asString(), teamMailbox.self.asString())
        val oldEntries = event.getAclDiff.getOldACL.getEntries.asScala

        SFlux(teamMailboxRepository.listMembers(teamMailbox))
          .map(_.username.asString())
          .collectSeq()
          .flatMapMany(memberSeq => {
            val memberUsernames = memberSeq.toSet
            SFlux.fromIterable(event.getAclDiff.getNewACL.getEntries.entrySet().asScala)
              .filter(e => !e.getKey.isNegative)
              .filter(e => NameType.user.equals(e.getKey.getNameType))
              .filter(e => !systemUsernames.contains(e.getKey.getName))
              .filter(e => !memberUsernames.contains(e.getKey.getName))
              .filter(e => !oldEntries.contains(e.getKey))
              .map(e => Username.of(e.getKey.getName))
              .flatMap(username => subscribeUser(username, event.getMailboxPath))
          })
          .`then`()
      case None => SMono.empty
    }

  private def subscribeUser(username: Username, path: MailboxPath): SMono[Unit] = {
    val session = mailboxManager.createSystemSession(username)
    SMono(subscriptionManager.subscribeReactive(path, session))
      .`then`()
      .onErrorResume(error => {
        logger.error(s"Failed to subscribe $username to $path", error)
        SMono.empty
      })
  }
}
