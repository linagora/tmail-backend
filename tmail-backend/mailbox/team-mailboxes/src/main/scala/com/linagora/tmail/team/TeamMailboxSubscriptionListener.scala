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
import org.apache.james.mailbox.events.MailboxEvents.{MailboxACLUpdated, MailboxAdded}
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.MailboxACL.{EntryKey, NameType, Rfc4314Rights, Right}
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.apache.james.mailbox.{MailboxManager, SubscriptionManager}
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

        membersOf(teamMailbox)
          .map(_.username)
          .collectSeq()
          .flatMapMany(members => {
            val memberUsernames = members.map(_.asString()).toSet

            val memberSubscriptions = SFlux.fromIterable(members)
              .flatMap(username => subscribeUser(username, event.getMailboxPath))

            val extraAclSubscriptions = SMono(mailboxManager.listRightsReactive(event.getMailboxPath, ownerSession))
              .onErrorResume {
                case _: MailboxNotFoundException => SMono.empty
                case e => SMono.error(e)
              }
              .flatMapMany(acl => rootExtraSenderEntries(teamMailbox, event.getMailboxPath)
                .flatMapMany(extraSenderEntries => SFlux.fromIterable(acl.getEntries.entrySet().asScala)
                  .filter(e => !isInheritedExtraSenderEntry(extraSenderEntries, e.getKey, e.getValue))))
              .filter(e => !e.getKey.isNegative)
              .filter(e => NameType.user.equals(e.getKey.getNameType))
              .filter(e => !systemUsernames.contains(e.getKey.getName))
              .filter(e => !memberUsernames.contains(e.getKey.getName))
              .map(e => Username.of(e.getKey.getName))
              .flatMap(username => subscribeUser(username, event.getMailboxPath))

            memberSubscriptions.mergeWith(extraAclSubscriptions)
          })
          .thenMany(SMono.empty)
      case None => SMono.empty
    }

  private def handleACLUpdated(event: MailboxACLUpdated): Publisher[Void] =
    TeamMailbox.from(event.getMailboxPath) match {
      case Some(teamMailbox) =>
        val systemUsernames = Set(teamMailbox.owner.asString(), teamMailbox.admin.asString(), teamMailbox.self.asString())
        val oldEntries = event.getAclDiff.getOldACL.getEntries.asScala

        membersOf(teamMailbox)
          .map(_.username.asString())
          .collectSeq()
          .flatMapMany(memberSeq => {
            val memberUsernames = memberSeq.toSet
            val addedEntries = event.getAclDiff.getNewACL.getEntries.entrySet().asScala.toSeq
              .filter(e => !e.getKey.isNegative)
              .filter(e => NameType.user.equals(e.getKey.getNameType))
              .filter(e => !systemUsernames.contains(e.getKey.getName))
              .filter(e => !memberUsernames.contains(e.getKey.getName))
              .filter(e => !oldEntries.contains(e.getKey))
              .map(e => e.getKey -> e.getValue)

            stripInheritedExtraSenderEntries(teamMailbox, event, addedEntries)
              .flatMapMany(stripped => SFlux.fromIterable(addedEntries)
                .filter(entry => !stripped.contains(entry._1))
                .map(entry => Username.of(entry._1.getName))
                .flatMap(username => subscribeUser(username, event.getMailboxPath)))
          })
          .thenMany(SMono.empty)
      case None => SMono.empty
    }

  // Extra senders are granted rights (Post) on the team mailbox root folder only. Upon creation, a folder inherits
  // the ACL of its parent: such inherited extra sender entries are removed, as they would outlive the extra sender.
  private def stripInheritedExtraSenderEntries(teamMailbox: TeamMailbox, event: MailboxACLUpdated,
                                               addedEntries: Seq[(EntryKey, Rfc4314Rights)]): SMono[Set[EntryKey]] =
    if (!event.getAclDiff.getOldACL.getEntries.isEmpty || addedEntries.isEmpty) {
      SMono.just(Set.empty)
    } else {
      val ownerSession = mailboxManager.createSystemSession(teamMailbox.owner)
      rootExtraSenderEntries(teamMailbox, event.getMailboxPath)
        .map(extraSenderEntries => addedEntries.filter(entry => isInheritedExtraSenderEntry(extraSenderEntries, entry._1, entry._2)))
        .flatMap(toStrip => {
          val strippedKeys: Set[EntryKey] = toStrip.map(_._1).toSet
          SFlux.fromIterable(toStrip)
            .concatMap(entry => SMono(mailboxManager.applyRightsCommandReactive(event.getMailboxPath,
              MailboxACL.command().key(entry._1).rights(entry._2).asRemoval(), ownerSession)))
            .onErrorResume {
              case _: MailboxNotFoundException => SFlux.empty
              case e => SFlux.error(e)
            }
            .collectSeq()
            .map(_ => strippedKeys)
        })
    }

  private def rootExtraSenderEntries(teamMailbox: TeamMailbox, path: MailboxPath): SMono[Map[EntryKey, Rfc4314Rights]] =
    if (path.equals(teamMailbox.mailboxPath)) {
      SMono.just(Map.empty)
    } else {
      SMono(mailboxManager.listRightsReactive(teamMailbox.mailboxPath, mailboxManager.createSystemSession(teamMailbox.owner)))
        .map(acl => acl.getEntries.asScala.toMap
          .filter(entry => entry._2.contains(Right.Post)))
        .onErrorResume {
          case _: MailboxNotFoundException => SMono.just(Map.empty)
          case e => SMono.error(e)
        }
    }

  private def isInheritedExtraSenderEntry(extraSenderEntries: Map[EntryKey, Rfc4314Rights], key: EntryKey, rights: Rfc4314Rights): Boolean =
    extraSenderEntries.get(key).contains(rights)

  // The team mailbox may have been deleted since the event was emitted (eg: events of its own deletion): nothing to do then
  private def membersOf(teamMailbox: TeamMailbox): SFlux[TeamMailboxMember] =
    SFlux(teamMailboxRepository.listMembers(teamMailbox))
      .onErrorResume {
        case _: TeamMailboxNotFoundException => SFlux.empty
        case e => SFlux.error(e)
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
