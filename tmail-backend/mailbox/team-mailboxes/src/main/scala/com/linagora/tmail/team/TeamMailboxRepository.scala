package com.linagora.tmail.team

import com.linagora.tmail.team.TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE
import com.linagora.tmail.team.TeamMailboxRepositoryImpl.{TEAM_MAILBOX_QUERY, TEAM_MAILBOX_RIGHTS_DEFAULT}
import javax.inject.Inject
import org.apache.james.core.{Domain, Username}
import org.apache.james.mailbox.exception.{MailboxExistsException, MailboxNotFoundException}
import org.apache.james.mailbox.model.MailboxACL.{NameType, Right}
import org.apache.james.mailbox.model.search.MailboxQuery
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, SessionProvider}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.util.Try

trait TeamMailboxRepository {

  def createTeamMailbox(teamMailbox: TeamMailbox): Publisher[Void]

  def deleteTeamMailbox(teamMailbox: TeamMailbox): Publisher[Void]

  def listTeamMailboxes(domain: Domain): Publisher[TeamMailbox]

  def listTeamMailboxes(username: Username): Publisher[TeamMailbox]

  def addMember(teamMailbox: TeamMailbox, addUser: Username): Publisher[Void]

  def removeMember(teamMailbox: TeamMailbox, removeUser: Username): Publisher[Void]

  def listMembers(teamMailbox: TeamMailbox): Publisher[Username]

  def exists(teamMailbox: TeamMailbox): Publisher[Boolean]

}

object TeamMailboxRepositoryImpl {
  val TEAM_MAILBOX_QUERY: MailboxQuery = MailboxQuery.builder
    .namespace(TEAM_MAILBOX_NAMESPACE)
    .matchesAllMailboxNames
    .build

  val TEAM_MAILBOX_RIGHTS_DEFAULT: MailboxACL.Rfc4314Rights =
    new MailboxACL.Rfc4314Rights(
      Right.Lookup,
      Right.Post,
      Right.Read,
      Right.WriteSeenFlag,
      Right.DeleteMessages,
      Right.Insert,
      Right.Write
    )
}

class TeamMailboxRepositoryImpl @Inject()(mailboxManager: MailboxManager,
                                          sessionProvider: SessionProvider) extends TeamMailboxRepository {

  override def createTeamMailbox(teamMailbox: TeamMailbox): Publisher[Void] = {
    val session: MailboxSession = createSession(teamMailbox)

    createMailboxReliably(teamMailbox.mailboxPath, session)
      .`then`(createMailboxReliably(teamMailbox.inboxPath, session))
      .`then`(createMailboxReliably(teamMailbox.sentPath, session))
      .`then`()
  }

  private def createMailboxReliably(path: MailboxPath, session: MailboxSession) =
    SMono.fromCallable(() => mailboxManager.createMailbox(path, session))
      .onErrorResume {
        case e: MailboxExistsException => SMono.empty
        case e => SMono.error(e)
      }

  override def deleteTeamMailbox(teamMailbox: TeamMailbox): Publisher[Void] = {
    val session: MailboxSession = createSession(teamMailbox)

    deleteReliably(teamMailbox.mailboxPath, session)
      .`then`(deleteReliably(teamMailbox.inboxPath, session))
      .`then`(deleteReliably(teamMailbox.sentPath, session))
      .`then`()
  }

  private def deleteReliably(path: MailboxPath, session: MailboxSession) =
    SMono.fromCallable(() => mailboxManager.deleteMailbox(path, session))
      .onErrorResume {
        case e: MailboxNotFoundException => SMono.empty
        case e => SMono.error(e)
      }

  private def createSession(teamMailbox: TeamMailbox): MailboxSession = createSession(teamMailbox.domain)

  private def createSession(domain: Domain): MailboxSession =
    sessionProvider.createSystemSession(Username.fromLocalPartWithDomain("team-mailbox", domain))

  override def listTeamMailboxes(domain: Domain): Publisher[TeamMailbox] =
    SFlux.fromPublisher(mailboxManager.search(TEAM_MAILBOX_QUERY, createSession(domain)))
      .filter(mailboxMetaData => mailboxMetaData.getPath.getUser.getDomainPart
        .filter(domain.equals(_)).isPresent)
      .flatMapIterable(mailboxMetaData => TeamMailbox.from(mailboxMetaData.getPath))
      .distinct()

  override def listTeamMailboxes(username: Username): Publisher[TeamMailbox] =
    SFlux.fromPublisher(mailboxManager.search(TEAM_MAILBOX_QUERY, sessionProvider.createSystemSession(username)))
      .flatMapIterable(mailboxMetaData => TeamMailbox.from(mailboxMetaData.getPath))
      .distinct()

  override def addMember(teamMailbox: TeamMailbox, user: Username): Publisher[Void] = {
    val session = createSession(teamMailbox)
    SMono.fromPublisher(exists(teamMailbox))
      .filter(teamMailboxExist => teamMailboxExist)
      .doOnNext(_ => {
        addRightForMember(teamMailbox.mailboxPath, user, session)
        addRightForMember(teamMailbox.inboxPath, user, session)
        addRightForMember(teamMailbox.sentPath, user, session)
      })
      .switchIfEmpty(SMono.error(TeamMailboxNotFoundException(teamMailbox)))
      .`then`()
  }

  private def addRightForMember(path: MailboxPath, user: Username, session: MailboxSession): Unit =
    Try(mailboxManager.applyRightsCommand(
      path,
      MailboxACL.command
        .forUser(user)
        .rights(TEAM_MAILBOX_RIGHTS_DEFAULT)
        .asAddition(),
      session))
      .fold(_ => (), u => u)

  private def removeRightForMember(path: MailboxPath, user: Username, session: MailboxSession): Unit =
    Try(mailboxManager.applyRightsCommand(
      path,
      MailboxACL.command
        .forUser(user)
        .rights(TEAM_MAILBOX_RIGHTS_DEFAULT)
        .asRemoval(),
      session))
      .fold(_ => (), u => u)

  override def removeMember(teamMailbox: TeamMailbox, user: Username): Publisher[Void] = {
    val session = createSession(teamMailbox)
    SMono.fromPublisher(isUserInTeamMailbox(teamMailbox, user))
      .filter(teamMailboxExist => teamMailboxExist)
      .doOnNext(_ => {
        removeRightForMember(teamMailbox.mailboxPath, user, session)
        removeRightForMember(teamMailbox.inboxPath, user, session)
        removeRightForMember(teamMailbox.sentPath, user, session)
      })
      .switchIfEmpty(SMono.error(TeamMailboxNotFoundException(teamMailbox)))
      .`then`()
  }

  override def listMembers(teamMailbox: TeamMailbox): Publisher[Username] = {
    val session: MailboxSession = createSession(teamMailbox)
    SMono.fromPublisher(exists(teamMailbox))
      .filter(b => b)
      .switchIfEmpty(SMono.error(TeamMailboxNotFoundException(teamMailbox)))
      .map(_ => mailboxManager.listRights(teamMailbox.mailboxPath, session))
      .flatMapIterable(mailboxACL => mailboxACL.getEntries.asScala)
      .map(entryKeyAndRights => entryKeyAndRights._1)
      .filter(entryKey => NameType.user.equals(entryKey.getNameType))
      .map(entryKey => Username.of(entryKey.getName))
      .distinct()
  }

  private def isUserInTeamMailbox(teamMailbox: TeamMailbox, checkUser: Username): SMono[Boolean] =
    SFlux.fromPublisher(listTeamMailboxes(checkUser))
      .filter(teamMailbox1 => teamMailbox1.equals(teamMailbox))
      .hasElements

  def exists(teamMailbox: TeamMailbox): SMono[Boolean] =
    SMono.fromPublisher(mailboxManager.mailboxExists(teamMailbox.mailboxPath, createSession(teamMailbox)))
      .map(b => b)
}
