package com.linagora.tmail.team

import com.linagora.tmail.team.TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE
import com.linagora.tmail.team.TeamMailboxRepositoryImpl.{TEAM_MAILBOX_QUERY, TEAM_MAILBOX_RIGHTS_DEFAULT}
import javax.inject.Inject
import org.apache.james.core.{Domain, Username}
import org.apache.james.mailbox.model.MailboxACL
import org.apache.james.mailbox.model.MailboxACL.{NameType, Right}
import org.apache.james.mailbox.model.search.MailboxQuery
import org.apache.james.mailbox.{MailboxManager, MailboxSession, SessionProvider}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

trait TeamMailboxRepository {

  def createTeamMailbox(teamMailbox: TeamMailbox): Publisher[Void]

  def deleteTeamMailbox(teamMailbox: TeamMailbox): Publisher[Void]

  def listTeamMailboxes(domain: Domain): Publisher[TeamMailbox]

  def listTeamMailboxes(username: Username): Publisher[TeamMailbox]

  def addMember(teamMailbox: TeamMailbox, addUser: Username): Publisher[Void]

  def removeMember(teamMailbox: TeamMailbox, removeUser: Username): Publisher[Void]

  def listMembers(teamMailbox: TeamMailbox): Publisher[Username]

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
      Right.Write
    )
}

class TeamMailboxRepositoryImpl @Inject()(mailboxManager: MailboxManager,
                                          sessionProvider: SessionProvider) extends TeamMailboxRepository {

  override def createTeamMailbox(teamMailbox: TeamMailbox): Publisher[Void] = {
    val session: MailboxSession = sessionProvider.createSystemSession(Username.of(teamMailbox.domain.asString))
    SMono.fromCallable(() => mailboxManager.createMailbox(teamMailbox.mailboxPath, session))
      .`then`(SMono.fromCallable(() => mailboxManager.createMailbox(teamMailbox.inboxPath, session)))
      .`then`(SMono.fromCallable(() => mailboxManager.createMailbox(teamMailbox.sentPath, session)))
      .`then`()
  }

  override def deleteTeamMailbox(teamMailbox: TeamMailbox): Publisher[Void] = {
    val session: MailboxSession = sessionProvider.createSystemSession(Username.of(teamMailbox.domain.asString))
    SMono.fromCallable(() => mailboxManager.deleteMailbox(teamMailbox.mailboxPath, session))
      .`then`(SMono.fromCallable(() => mailboxManager.deleteMailbox(teamMailbox.inboxPath, session)))
      .`then`(SMono.fromCallable(() => mailboxManager.deleteMailbox(teamMailbox.sentPath, session)))
      .`then`()
  }

  override def listTeamMailboxes(domain: Domain): Publisher[TeamMailbox] = {
    val session: MailboxSession = sessionProvider.createSystemSession(Username.of(domain.asString()))
    SFlux.fromPublisher(mailboxManager.search(TEAM_MAILBOX_QUERY, session))
      .filter(mailboxMetaData => domain.asString().equals(mailboxMetaData.getPath.getUser.getLocalPart))
      .flatMapIterable(mailboxMetaData => TeamMailbox.from(mailboxMetaData.getPath))
  }

  override def listTeamMailboxes(username: Username): Publisher[TeamMailbox] =
    SFlux.fromPublisher(mailboxManager.search(TEAM_MAILBOX_QUERY, sessionProvider.createSystemSession(username)))
      .flatMapIterable(mailboxMetaData => TeamMailbox.from(mailboxMetaData.getPath))

  override def addMember(teamMailbox: TeamMailbox, addUser: Username): Publisher[Void] =
    SMono.fromPublisher(isExistTeamMailbox(teamMailbox))
      .filter(teamMailboxExist => teamMailboxExist)
      .doOnNext(_ => mailboxManager.applyRightsCommand(
        teamMailbox.mailboxPath,
        MailboxACL.command
          .forUser(addUser)
          .rights(TEAM_MAILBOX_RIGHTS_DEFAULT)
          .asAddition(),
        sessionProvider.createSystemSession(Username.of(teamMailbox.domain.asString))))
      .switchIfEmpty(SMono.error(TeamMailboxNotFoundException()))
      .`then`()

  override def removeMember(teamMailbox: TeamMailbox, removeUser: Username): Publisher[Void] =
    SMono.fromPublisher(isUserInTeamMailbox(teamMailbox, removeUser))
      .filter(teamMailboxExist => teamMailboxExist)
      .doOnNext(_ => mailboxManager.applyRightsCommand(
        teamMailbox.mailboxPath,
        MailboxACL.command
          .forUser(removeUser)
          .rights(TEAM_MAILBOX_RIGHTS_DEFAULT)
          .asRemoval(),
        sessionProvider.createSystemSession(Username.of(teamMailbox.domain.asString))))
      .switchIfEmpty(SMono.error(TeamMailboxNotFoundException()))
      .`then`()

  override def listMembers(teamMailbox: TeamMailbox): Publisher[Username] = {
    val session: MailboxSession = sessionProvider.createSystemSession(Username.of(teamMailbox.domain.asString))
    SMono.fromCallable(() => mailboxManager.listRights(teamMailbox.mailboxPath, session))
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

  private def isExistTeamMailbox(teamMailbox: TeamMailbox): SMono[Boolean] =
    SFlux.fromPublisher(listTeamMailboxes(teamMailbox.domain))
      .filter(teamMailbox1 => teamMailbox1.equals(teamMailbox))
      .hasElements
}
