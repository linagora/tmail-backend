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

import java.util.{Set => JavaSet}
import com.google.common.collect.ImmutableSet
import com.linagora.tmail.team.TeamMailbox.TEAM_MAILBOX_ADMIN_LOCAL_PART
import com.linagora.tmail.team.TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE
import com.linagora.tmail.team.TeamMailboxRepositoryImpl.{BASIC_TEAM_MAILBOX_RIGHTS, TEAM_MAILBOX_MANAGER_RIGHTS, TEAM_MAILBOX_MEMBER_RIGHTS, TEAM_MAILBOX_MEMBER_RIGHTS_DELETE, TEAM_MAILBOX_QUERY}
import com.linagora.tmail.team.TeamMailboxUserEntityValidator.TEAM_MAILBOX
import com.linagora.tmail.team.TeamMemberRole.{ManagerRole, MemberRole}
import jakarta.inject.Inject
import org.apache.james.UserEntityValidator
import org.apache.james.core.{Domain, Username}
import org.apache.james.mailbox.exception.{MailboxExistsException, MailboxNotFoundException}
import org.apache.james.mailbox.model.MailboxACL.{EntryKey, NameType, Rfc4314Rights, Right}
import org.apache.james.mailbox.model.search.{MailboxQuery, PrefixedWildcard}
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.apache.james.mailbox.store.MailboxSessionMapperFactory
import org.apache.james.mailbox.{MailboxManager, MailboxSession, SubscriptionManager}
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

trait TeamMailboxRepository {

  def createTeamMailbox(teamMailbox: TeamMailbox): Publisher[Void]

  def deleteTeamMailbox(teamMailbox: TeamMailbox): Publisher[Void]

  def listTeamMailboxes(domain: Domain): Publisher[TeamMailbox]

  def listTeamMailboxes(username: Username): Publisher[TeamMailbox]

  def listTeamMailboxes(): Publisher[TeamMailbox]

  def addMember(teamMailbox: TeamMailbox, addTeamMailboxMember: TeamMailboxMember): Publisher[Void]

  def removeMember(teamMailbox: TeamMailbox, removeUser: Username): Publisher[Void]

  def listMembers(teamMailbox: TeamMailbox): Publisher[TeamMailboxMember]

  def exists(teamMailbox: TeamMailbox): Publisher[Boolean]

}

object TeamMailboxRepositoryImpl {
  val TEAM_MAILBOX_QUERY: MailboxQuery = MailboxQuery.builder
    .namespace(TEAM_MAILBOX_NAMESPACE)
    .matchesAllMailboxNames
    .build

  val BASIC_TEAM_MAILBOX_RIGHTS: java.util.Collection[Right] =
    ImmutableSet.of(Right.Lookup,
      Right.Post,
      Right.Read,
      Right.WriteSeenFlag,
      Right.DeleteMessages,
      Right.Insert,
      Right.Write,
      Right.CreateMailbox,
      Right.PerformExpunge)

  val TEAM_MAILBOX_MEMBER_RIGHTS: MailboxACL.Rfc4314Rights =
    new MailboxACL.Rfc4314Rights(BASIC_TEAM_MAILBOX_RIGHTS)

  val TEAM_MAILBOX_MANAGER_RIGHTS: MailboxACL.Rfc4314Rights = TEAM_MAILBOX_MEMBER_RIGHTS
    .union(new MailboxACL.Rfc4314Rights(Right.Administer))

  val TEAM_MAILBOX_MEMBER_RIGHTS_DELETE: MailboxACL.Rfc4314Rights =
    TEAM_MAILBOX_MANAGER_RIGHTS.union(new MailboxACL.Rfc4314Rights(Right.DeleteMailbox))
}

class TeamMailboxRepositoryImpl @Inject()(mailboxManager: MailboxManager,
                                          subscriptionManager: SubscriptionManager,
                                          mailboxSessionMapperFactory: MailboxSessionMapperFactory,
                                          teamMailboxCallbackSetJava: JavaSet[TeamMailboxCallback]) extends TeamMailboxRepository {
  private val teamMailboxCallbackSetScala: Set[TeamMailboxCallback] = teamMailboxCallbackSetJava.asScala.toSet

  private var teamMailboxEntityValidator: UserEntityValidator = new TeamMailboxUserEntityValidator(this)

  @Inject
  def setValidator(teamMailboxEntityValidator: UserEntityValidator): Unit =
    this.teamMailboxEntityValidator = teamMailboxEntityValidator

  override def createTeamMailbox(teamMailbox: TeamMailbox): Publisher[Void] = {
    val session: MailboxSession = createSession(teamMailbox)
    val username = Username.fromMailAddress(teamMailbox.asMailAddress)

    SMono.fromCallable(() => teamMailboxEntityValidator.canCreate(username, ImmutableSet.of(TEAM_MAILBOX)))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      .flatMap[Unit](maybeValidationFailure => maybeValidationFailure.toScala match {
        case Some(validationFailure) => SMono.error(TeamMailboxNameConflictException(validationFailure.errorMessage))
        case None => createDefaultMailboxReliably(teamMailbox, session)
      })
      .`then`(SFlux.fromIterable(teamMailboxCallbackSetScala)
        .flatMap(_.teamMailboxAdded(teamMailbox), ReactorUtils.DEFAULT_CONCURRENCY)
        .collectSeq()
        .`then`(SMono.empty))
  }

  private def createDefaultMailboxReliably(teamMailbox: TeamMailbox, session: MailboxSession) =
    SFlux.fromIterable(teamMailbox.defaultMailboxPaths)
      .flatMap(mailboxPath => createMailboxReliably(mailboxPath, teamMailbox, session), ReactorUtils.DEFAULT_CONCURRENCY)
      .`then`()

  private def createMailboxReliably(path: MailboxPath, teamMailbox: TeamMailbox, session: MailboxSession) =
    SMono(mailboxManager.createMailboxReactive(path, session))
      .`then`(SMono(mailboxManager.applyRightsCommandReactive(path, MailboxACL.command.key(EntryKey.createUserEntryKey(teamMailbox.admin, false)).rights(MailboxACL.FULL_RIGHTS).asAddition(), session)))
      .onErrorResume {
        case _: MailboxExistsException => SMono.empty
        case e => SMono.error(e)
      }

  override def deleteTeamMailbox(teamMailbox: TeamMailbox): Publisher[Void] =
    deleteDefaultMailboxReliably(teamMailbox, createSession(teamMailbox))
      .`then`(SFlux.fromIterable(teamMailboxCallbackSetScala)
        .flatMap(_.teamMailboxRemoved(teamMailbox), ReactorUtils.DEFAULT_CONCURRENCY)
        .collectSeq()
        .`then`(SMono.empty))

  private def deleteDefaultMailboxReliably(teamMailbox: TeamMailbox, session: MailboxSession) =
    SFlux.fromIterable(teamMailbox.defaultMailboxPaths)
      .flatMap(mailboxPath => deleteReliably(mailboxPath, session), ReactorUtils.DEFAULT_CONCURRENCY)
      .`then`()

  private def deleteReliably(path: MailboxPath, session: MailboxSession) =
    SMono(mailboxManager.deleteMailboxReactive(path, session))
      .onErrorResume {
        case _: MailboxNotFoundException => SMono.empty
        case e => SMono.error(e)
      }

  private def createSession(teamMailbox: TeamMailbox): MailboxSession =
    mailboxManager.createSystemSession(teamMailbox.owner)

  override def listTeamMailboxes(domain: Domain): Publisher[TeamMailbox] = {
    val session = mailboxManager.createSystemSession(Username.fromLocalPartWithDomain(TEAM_MAILBOX_ADMIN_LOCAL_PART, domain))
    SFlux.fromPublisher(mailboxManager.search(TEAM_MAILBOX_QUERY, session))
      .flatMapIterable(mailboxMetaData => TeamMailbox.from(mailboxMetaData.getPath))
      .distinct()
  }

  override def listTeamMailboxes(username: Username): Publisher[TeamMailbox] =
    SFlux.fromPublisher(mailboxManager.search(TEAM_MAILBOX_QUERY, mailboxManager.createSystemSession(username)))
      .flatMapIterable(mailboxMetaData => TeamMailbox.from(mailboxMetaData.getPath))
      .distinct()

  override def addMember(teamMailbox: TeamMailbox, teamMailboxMember: TeamMailboxMember): Publisher[Void] = {
    val session = createSession(teamMailbox)
    val memberSession = mailboxManager.createSystemSession(teamMailboxMember.username)
    SMono.fromPublisher(exists(teamMailbox))
      .filter(teamMailboxExist => teamMailboxExist)
      .switchIfEmpty(SMono.error(TeamMailboxNotFoundException(teamMailbox)))
      .flatMapMany(_ => listMailboxPaths(teamMailbox, session))
      .flatMap(mailboxPath => addRightForMember(mailboxPath, teamMailboxMember.username, session, teamMailboxMember.role)
        .`then`(subscribeForMember(mailboxPath, memberSession)), ReactorUtils.DEFAULT_CONCURRENCY)
      .`then`()
  }

  private def listMailboxPaths(teamMailbox: TeamMailbox, session: MailboxSession): SFlux[MailboxPath] =
     SFlux(mailboxSessionMapperFactory.getMailboxMapper(session)
      .findMailboxWithPathLike(MailboxQuery.builder
        .namespace(TEAM_MAILBOX_NAMESPACE)
        .expression(new PrefixedWildcard(teamMailbox.mailboxName.asString()))
        .user(session.getUser)
        .build
        .asUserBound))
      .map(_.generateAssociatedPath())

  private def addRightForMember(path: MailboxPath, user: Username, session: MailboxSession, teamMailboxRole: TeamMemberRole): SMono[Unit] =
    SMono(mailboxManager.applyRightsCommandReactive(path,
      MailboxACL.command
        .forUser(user)
        .rights(rightsToAdd(teamMailboxRole))
        .asReplacement(),
      session))
      .`then`()

  private def removeAdministerRightIfNeeded(path: MailboxPath, user: Username, session: MailboxSession, teamMailboxRole: TeamMemberRole): SMono[Void] =
    teamMailboxRole.value match {
      case MemberRole => SMono(mailboxManager.applyRightsCommandReactive(path,
        MailboxACL.command
          .forUser(user)
          .rights(Right.Administer)
          .asRemoval(),
        session))
      case ManagerRole => SMono.empty
    }

  private def rightsToAdd(teamMailboxRole: TeamMemberRole): MailboxACL.Rfc4314Rights =
    teamMailboxRole.value match {
      case MemberRole => TEAM_MAILBOX_MEMBER_RIGHTS  // The `DeleteMailbox` right will be added in PropagateDeleteMailboxRightListener
      case ManagerRole => TEAM_MAILBOX_MANAGER_RIGHTS
    }

  private def subscribeForMember(path: MailboxPath, memberSession: MailboxSession): SMono[Unit] =
    SMono(subscriptionManager.subscribeReactive(path, memberSession)).`then`()

  private def removeRightForMember(path: MailboxPath, user: Username, session: MailboxSession): SMono[Unit] =
    SMono(mailboxManager.applyRightsCommandReactive(
      path,
      MailboxACL.command
        .forUser(user)
        .rights(TEAM_MAILBOX_MEMBER_RIGHTS_DELETE)
        .asRemoval(),
      session))
      .`then`()

  private def unSubscribeForMember(path: MailboxPath, memberSession: MailboxSession): SMono[Unit] =
    SMono(subscriptionManager.unsubscribeReactive(path, memberSession)).`then`()

  override def removeMember(teamMailbox: TeamMailbox, user: Username): Publisher[Void] = {
    val session = createSession(teamMailbox)
    val memberSession = mailboxManager.createSystemSession(user)
    SMono.fromPublisher(exists(teamMailbox))
      .filter(mailboxExists => mailboxExists)
      .switchIfEmpty(SMono.error(TeamMailboxNotFoundException(teamMailbox)))
      .flatMap(_ => SMono.fromPublisher(isUserInTeamMailbox(teamMailbox, user))
        .filter(userInTeamMailbox => userInTeamMailbox)
        .flatMapMany(_ => listMailboxPaths(teamMailbox, session))
        .flatMap(mailboxPath => removeRightForMember(mailboxPath, user, session)
          .`then`(unSubscribeForMember(mailboxPath, memberSession)))
        .`then`())
      .`then`()
  }

  override def listTeamMailboxes(): SFlux[TeamMailbox] = {
    val session = mailboxManager.createSystemSession(Username.of("team-mailboxes"))
    SFlux.fromIterable(mailboxManager.list(session)
      .asScala
      .flatMap(TeamMailbox.from)
      .distinct
      .toSeq)
  }

  override def listMembers(teamMailbox: TeamMailbox): Publisher[TeamMailboxMember] = {
    val session: MailboxSession = createSession(teamMailbox)
    SMono.fromPublisher(exists(teamMailbox))
      .filter(b => b)
      .switchIfEmpty(SMono.error(TeamMailboxNotFoundException(teamMailbox)))
      .flatMap(_ => SMono(mailboxManager.listRightsReactive(teamMailbox.mailboxPath, session)))
      .flatMapIterable(mailboxACL => mailboxACL.getEntries.asScala)
      .filter(entryKeyAndRights => NameType.user.equals(entryKeyAndRights._1.getNameType))
      .map(entryKeyAndRights => Username.of(entryKeyAndRights._1.getName) -> entryKeyAndRights._2)
      .distinct(usernameAndRights => usernameAndRights._1)
      .filter(usernameAndRights => !usernameAndRights._1.equals(teamMailbox.admin))
      .filter(usernameAndRights => usernameAndRights._2.list().containsAll(BASIC_TEAM_MAILBOX_RIGHTS))
      .map(usernameAndRights => TeamMailboxMember(username = usernameAndRights._1, role = getTeamMemberRole(usernameAndRights._2)))
  }

  private def getTeamMemberRole(rights: MailboxACL.Rfc4314Rights): TeamMemberRole =
    if (rights.contains(Right.Administer)) {
      TeamMemberRole(ManagerRole)
    } else {
      TeamMemberRole(MemberRole)
    }

  private def isUserInTeamMailbox(teamMailbox: TeamMailbox, checkUser: Username): SMono[Boolean] =
    SFlux.fromPublisher(listTeamMailboxes(checkUser))
      .filter(teamMailbox1 => teamMailbox1.equals(teamMailbox))
      .hasElements

  def exists(teamMailbox: TeamMailbox): SMono[Boolean] =
    SMono.fromPublisher(mailboxManager.mailboxExists(teamMailbox.mailboxPath, createSession(teamMailbox)))
      .map(b => b)
}
