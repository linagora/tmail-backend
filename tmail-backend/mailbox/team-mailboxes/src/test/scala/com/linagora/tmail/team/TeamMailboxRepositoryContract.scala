package com.linagora.tmail.team

import java.util.{Map => JavaMap, Set => JavaSet}

import com.linagora.tmail.team.TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE
import com.linagora.tmail.team.TeamMailboxRepositoryContract.{ANDRE, BOB, DOMAIN_1, DOMAIN_2, TEAM_MAILBOX_DOMAIN_1, TEAM_MAILBOX_DOMAIN_2, TEAM_MAILBOX_MARKETING, TEAM_MAILBOX_SALES}
import eu.timepit.refined.auto._
import org.apache.james.adapter.mailbox.{ACLUsernameChangeTaskStep, MailboxUsernameChangeTaskStep}
import org.apache.james.core.{Domain, Username}
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.model.search.MailboxQuery
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.apache.james.mailbox.store.StoreSubscriptionManager
import org.apache.james.mailbox.{MailboxManager, MailboxSession, SubscriptionManager}
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{BeforeEach, Test}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object TeamMailboxRepositoryContract {
  val DOMAIN_1: Domain = Domain.of("linagora.com")
  val DOMAIN_2: Domain = Domain.of("linagora2.com")
  val TEAM_MAILBOX_DOMAIN_1: Username = Username.fromLocalPartWithDomain("team-mailbox", DOMAIN_1)
  val TEAM_MAILBOX_DOMAIN_2: Username = Username.fromLocalPartWithDomain("team-mailbox", DOMAIN_2)
  val TEAM_MAILBOX_MARKETING: TeamMailbox = TeamMailbox(DOMAIN_1, TeamMailboxName("marketing"))
  val TEAM_MAILBOX_SALES: TeamMailbox = TeamMailbox(DOMAIN_1, TeamMailboxName("sale"))
  val BOB: Username = Username.of("bob@linagora.com")
  val ANDRE: Username = Username.of("andre@linagora.com")
}

trait TeamMailboxRepositoryContract {

  def testee: TeamMailboxRepository

  def mailboxManager: MailboxManager

  def subscriptionManager: SubscriptionManager

  @Test
  def createTeamMailboxShouldStoreDefaultAssignMailboxes(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    val session: MailboxSession = mailboxManager.createSystemSession(TEAM_MAILBOX_DOMAIN_1)

    assertThat(mailboxManager.list(session))
      .containsExactlyInAnyOrder(
        new MailboxPath(TEAM_MAILBOX_NAMESPACE, TEAM_MAILBOX_DOMAIN_1, s"marketing.INBOX"),
        new MailboxPath(TEAM_MAILBOX_NAMESPACE, TEAM_MAILBOX_DOMAIN_1, s"marketing.Outbox"),
        new MailboxPath(TEAM_MAILBOX_NAMESPACE, TEAM_MAILBOX_DOMAIN_1, s"marketing.Sent"),
        new MailboxPath(TEAM_MAILBOX_NAMESPACE, TEAM_MAILBOX_DOMAIN_1, s"marketing.Trash"),
        new MailboxPath(TEAM_MAILBOX_NAMESPACE, TEAM_MAILBOX_DOMAIN_1, s"marketing.Drafts"),
        new MailboxPath(TEAM_MAILBOX_NAMESPACE, TEAM_MAILBOX_DOMAIN_1, s"marketing"))
  }

  @Test
  def existsShouldReturnTrueAfterCreate(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()

    assertThat(SMono.fromPublisher(testee.exists(TEAM_MAILBOX_MARKETING)).block())
      .isTrue
  }

  @Test
  def existsShouldReturnFalseByDefault(): Unit = {
    assertThat(SMono.fromPublisher(testee.exists(TEAM_MAILBOX_MARKETING)).block())
      .isFalse
  }

  @Test
  def createTeamMailboxShouldNotThrowWhenAssignMailboxExists(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()

    assertThatCode(() => SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def createMailboxWithSamePathShouldFailWhenTeamMailboxExists(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    val session: MailboxSession = mailboxManager.createSystemSession(TEAM_MAILBOX_DOMAIN_1)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThatThrownBy(() => mailboxManager.createMailbox(TEAM_MAILBOX_MARKETING.mailboxPath, session))
        .hasMessageContaining(s"${TEAM_MAILBOX_MARKETING.mailboxPath.toString} already exists.")

      softly.assertThatThrownBy(() => mailboxManager.createMailbox(TEAM_MAILBOX_MARKETING.inboxPath, session))
        .hasMessageContaining(s"${TEAM_MAILBOX_MARKETING.inboxPath.toString} already exists.")

      softly.assertThatThrownBy(() => mailboxManager.createMailbox(TEAM_MAILBOX_MARKETING.sentPath, session))
        .hasMessageContaining(s"${TEAM_MAILBOX_MARKETING.sentPath.toString} already exists.")
    })
  }

  @Test
  def deleteTeamMailboxShouldRemoveAllDefaultMailboxes(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.deleteTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    val session: MailboxSession = mailboxManager.createSystemSession(TEAM_MAILBOX_DOMAIN_1)

    assertThat(SFlux.fromIterable(TEAM_MAILBOX_MARKETING.defaultMailboxPaths)
      .flatMap(mailboxPath => mailboxManager.mailboxExists(mailboxPath, session))
      .filter(exists => exists)
      .count()
      .block())
      .isEqualTo(0)
  }

  @Test
  def deleteTeamMailboxShouldNotRemoveOtherTeamMailboxes(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_SALES)).block()
    SMono.fromPublisher(testee.deleteTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    val session: MailboxSession = mailboxManager.createSystemSession(TEAM_MAILBOX_DOMAIN_2)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SMono.fromPublisher(mailboxManager.mailboxExists(TEAM_MAILBOX_SALES.mailboxPath, session))
        .block())
        .isTrue
      softly.assertThat(SMono.fromPublisher(mailboxManager.mailboxExists(TEAM_MAILBOX_SALES.inboxPath, session))
        .block())
        .isTrue
      softly.assertThat(SMono.fromPublisher(mailboxManager.mailboxExists(TEAM_MAILBOX_SALES.sentPath, session))
        .block())
        .isTrue
    })
  }

  @Test
  def deleteTeamMailboxShouldNotThrowWhenAssignMailboxDoesNotExists(): Unit = {
    assertThatCode(() => SMono.fromPublisher(testee.deleteTeamMailbox(TEAM_MAILBOX_MARKETING)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def addMemberShouldThrowWhenTeamMailboxDoesNotExists(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block())
      .isInstanceOf(classOf[TeamMailboxNotFoundException])
  }

  @Test
  def addMemberShouldAddImplicitRights(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()
    val bobSession: MailboxSession = mailboxManager.createSystemSession(BOB)
    val entriesRights: JavaMap[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX_MARKETING.mailboxPath, bobSession).getEntries

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(entriesRights)
        .hasSize(1)
      softly.assertThat(entriesRights.asScala.head._2.toString)
        .isEqualTo("ilprstw")
    })
  }

  @Test
  def addMemberShouldNotAddAdministerRight(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()
    val entriesRights: JavaMap[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX_MARKETING.mailboxPath, mailboxManager.createSystemSession(BOB)).getEntries

    assertThat(entriesRights.asScala.head._2.contains(MailboxACL.Right.Administer))
      .isFalse
  }

  @Test
  def addMemberShouldBeIdempotence(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()

    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()

    val entriesRights: JavaMap[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX_MARKETING.mailboxPath, mailboxManager.createSystemSession(BOB)).getEntries

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava)
        .containsOnly(TeamMailboxMember.asMember(BOB))
      softly.assertThat(entriesRights.asScala.head._2.toString)
        .isEqualTo("ilprstw")
    })
  }

  @Test
  def promoteMemberToManagerShouldSucceed(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()

    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asManager(BOB))).block()

    val entriesRights: JavaMap[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX_MARKETING.mailboxPath, mailboxManager.createSystemSession(BOB)).getEntries

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava)
        .containsOnly(TeamMailboxMember.asManager(BOB))
      softly.assertThat(entriesRights.asScala.head._2.contains(MailboxACL.Right.Administer))
        .isTrue
    })
  }

  @Test
  def addManagerShouldAddAdministerRight(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asManager(BOB))).block()
    val bobSession: MailboxSession = mailboxManager.createSystemSession(BOB)
    val entriesRights: JavaMap[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX_MARKETING.mailboxPath, bobSession).getEntries

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(entriesRights)
        .hasSize(1)
      softly.assertThat(entriesRights.asScala.head._2.contains(MailboxACL.Right.Administer))
        .isTrue
      softly.assertThat(entriesRights.asScala.head._2.toString)
        .isEqualTo("ailprstw")
    })
  }

  @Test
  def addManagerShouldBeIdempotence(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asManager(BOB))).block()

    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asManager(BOB))).block()

    val entriesRights: JavaMap[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX_MARKETING.mailboxPath, mailboxManager.createSystemSession(BOB)).getEntries

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava)
        .containsOnly(TeamMailboxMember.asManager(BOB))
      softly.assertThat(entriesRights.asScala.head._2.toString)
        .isEqualTo("ailprstw")
    })
  }

  @Test
  def demoteManagerToMemberShouldSucceed(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asManager(BOB))).block()

    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()

    val entriesRights: JavaMap[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX_MARKETING.mailboxPath, mailboxManager.createSystemSession(BOB)).getEntries

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava)
        .containsOnly(TeamMailboxMember.asMember(BOB))
      softly.assertThat(entriesRights.asScala.head._2.contains(MailboxACL.Right.Administer))
        .isFalse
    })
  }

  @Test
  def addMemberShouldAddImplicitRightsToTeamMailboxSubfolders(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()
    val bobSession: MailboxSession = mailboxManager.createSystemSession(BOB)
    val inboxEntriesRights: JavaMap[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX_MARKETING.inboxPath, bobSession).getEntries
    val sentEntriesRights: JavaMap[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX_MARKETING.sentPath, bobSession).getEntries
    val outboxEntriesRights: JavaMap[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX_MARKETING.mailboxPath("Outbox"), bobSession).getEntries
    val draftsEntriesRights: JavaMap[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX_MARKETING.mailboxPath("Drafts"), bobSession).getEntries
    val trashEntriesRights: JavaMap[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX_MARKETING.mailboxPath("Trash"), bobSession).getEntries

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(inboxEntriesRights)
        .hasSize(1)
      softly.assertThat(inboxEntriesRights.asScala.head._2.toString)
        .isEqualTo("ilprstw")

      softly.assertThat(sentEntriesRights)
        .hasSize(1)
      softly.assertThat(sentEntriesRights.asScala.head._2.toString)
        .isEqualTo("ilprstw")

      softly.assertThat(outboxEntriesRights)
        .hasSize(1)
      softly.assertThat(outboxEntriesRights.asScala.head._2.toString)
        .isEqualTo("ilprstw")

      softly.assertThat(draftsEntriesRights)
        .hasSize(1)
      softly.assertThat(draftsEntriesRights.asScala.head._2.toString)
        .isEqualTo("ilprstw")

      softly.assertThat(trashEntriesRights)
        .hasSize(1)
      softly.assertThat(trashEntriesRights.asScala.head._2.toString)
        .isEqualTo("ilprstw")
    })
  }

  @Test
  def addMemberShouldSubscribeTeamMailboxByDefault(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()
    val bobSession: MailboxSession = mailboxManager.createSystemSession(BOB)

    assertThat(SFlux(subscriptionManager.subscriptionsReactive(bobSession))
      .collectSeq()
      .block().asJava)
      .containsExactlyInAnyOrder(TEAM_MAILBOX_MARKETING.mailboxPath, TEAM_MAILBOX_MARKETING.inboxPath, TEAM_MAILBOX_MARKETING.sentPath,
        TEAM_MAILBOX_MARKETING.mailboxPath("Drafts"), TEAM_MAILBOX_MARKETING.mailboxPath("Outbox"), TEAM_MAILBOX_MARKETING.mailboxPath("Trash"))
  }

  @Test
  def removeMemberShouldThrowWhenTeamMailboxDoesNotExists(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.removeMember(TEAM_MAILBOX_MARKETING, BOB)).block())
      .isInstanceOf(classOf[TeamMailboxNotFoundException])
  }

  @Test
  def removeMemberShouldBeIdempotent(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()

    SMono.fromPublisher(testee.removeMember(TEAM_MAILBOX_MARKETING, BOB)).block()
    assertThatCode(() => SMono.fromPublisher(testee.removeMember(TEAM_MAILBOX_MARKETING, BOB)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def removeMemberShouldRevokeMemberRightsFromTeamMailbox(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()
    SMono.fromPublisher(testee.removeMember(TEAM_MAILBOX_MARKETING, BOB)).block()

    val entriesRights: JavaMap[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX_MARKETING.mailboxPath, mailboxManager.createSystemSession(BOB)).getEntries

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava)
        .doesNotContain(TeamMailboxMember.asMember(BOB))
      softly.assertThat(entriesRights)
        .isEmpty()
    })
  }

  @Test
  def removeManagerShouldRevokeManagerRightsFromTeamMailbox(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asManager(BOB))).block()
    SMono.fromPublisher(testee.removeMember(TEAM_MAILBOX_MARKETING, BOB)).block()

    val entriesRights: JavaMap[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX_MARKETING.mailboxPath, mailboxManager.createSystemSession(BOB)).getEntries

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava)
        .doesNotContain(TeamMailboxMember.asManager(BOB))
      softly.assertThat(entriesRights)
        .isEmpty()
    })
  }

  @Test
  def removeMemberShouldUnSubscribeTeamMailbox(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()
    SMono.fromPublisher(testee.removeMember(TEAM_MAILBOX_MARKETING, BOB)).block()

    assertThat(SFlux(subscriptionManager.subscriptionsReactive(mailboxManager.createSystemSession(BOB)))
      .collectSeq()
      .block().asJava)
      .doesNotContain(TEAM_MAILBOX_MARKETING.mailboxPath, TEAM_MAILBOX_MARKETING.inboxPath, TEAM_MAILBOX_MARKETING.sentPath,
        TEAM_MAILBOX_MARKETING.mailboxPath("Drafts"), TEAM_MAILBOX_MARKETING.mailboxPath("Outbox"), TEAM_MAILBOX_MARKETING.mailboxPath("Trash"))
  }

  @Test
  def removeMemberShouldNotRevokeOtherMembers(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(ANDRE))).block()
    SMono.fromPublisher(testee.removeMember(TEAM_MAILBOX_MARKETING, BOB)).block()

    assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava)
      .contains(TeamMailboxMember.asMember(ANDRE))
  }

  @Test
  def listMemberShouldReturnEmptyWhenDefault(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()

    assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def listMemberShouldReturnListMembersHaveRightsWhenSingleMember(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()

    assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(TeamMailboxMember.asMember(BOB))
  }

  @Test
  def listMemberShouldReturnListMembersHaveRightsWhenSeveralMembers(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(ANDRE))).block()

    assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(TeamMailboxMember.asMember(BOB), TeamMailboxMember.asMember(ANDRE))
  }

  @Test
  def listMemberShouldThrowWhenTeamMailboxDoesNotExists(): Unit = {
    assertThatThrownBy(()=>SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava)
      .isInstanceOf(classOf[TeamMailboxNotFoundException])
  }

  @Test
  def listTeamMailboxesByUserShouldReturnEmptyByDefault(): Unit = {
    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(BOB)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def listTeamMailboxesByUserShouldReturnTeamMailboxesWhichUserIsMemberOf(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()

    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(BOB)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(TEAM_MAILBOX_MARKETING)
  }

  @Test
  def listTeamMailboxesByUserShouldNotReturnTeamMailboxesWhichUserIsNotMemberOf(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(ANDRE))).block()

    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(BOB)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def userCanBeMemberOfSeveralTeamMailboxes(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_SALES)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_SALES, TeamMailboxMember.asMember(BOB))).block()

    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(BOB)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(TEAM_MAILBOX_MARKETING, TEAM_MAILBOX_SALES)
  }

  @Test
  def listTeamMailboxesByDomainShouldReturnEmptyByDefault(): Unit = {
    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(DOMAIN_1)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def listTeamMailboxesByDomainShouldReturnStoredEntriesWhenSingle(): Unit = {
    val saleTeam: TeamMailbox = TeamMailbox(DOMAIN_1, TeamMailboxName("sale"))
    SMono.fromPublisher(testee.createTeamMailbox(saleTeam)).block()

    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(DOMAIN_1)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(saleTeam)
  }

  @Test
  def listTeamMailboxesByDomainShouldReturnStoredEntriesWhenMultiple(): Unit = {
    val saleTeam: TeamMailbox = TeamMailbox(DOMAIN_1, TeamMailboxName("sale"))
    val marketingTeam: TeamMailbox = TeamMailbox(DOMAIN_1, TeamMailboxName("marketing"))
    SMono.fromPublisher(testee.createTeamMailbox(saleTeam)).block()
    SMono.fromPublisher(testee.createTeamMailbox(marketingTeam)).block()

    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(DOMAIN_1)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(saleTeam, marketingTeam)
  }

  @Test
  def listTeamMailboxesByDomainShouldNotReturnUnRelatedEntries(): Unit = {
    val saleTeam: TeamMailbox = TeamMailbox(DOMAIN_1, TeamMailboxName("sale"))
    val marketingTeam: TeamMailbox = TeamMailbox(DOMAIN_2, TeamMailboxName("marketing"))
    SMono.fromPublisher(testee.createTeamMailbox(saleTeam)).block()
    SMono.fromPublisher(testee.createTeamMailbox(marketingTeam)).block()

    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(DOMAIN_1)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(saleTeam)
  }

  @Test
  def listAllTeamMailboxesShouldReturnStoredEntriesWhenSingle(): Unit = {
    val saleTeam: TeamMailbox = TeamMailbox(DOMAIN_1, TeamMailboxName("sale"))
    SMono.fromPublisher(testee.createTeamMailbox(saleTeam)).block()

    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(DOMAIN_1)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(saleTeam)
  }

  @Test
  def listAllTeamMailboxesShouldReturnAllEntries(): Unit = {
    val saleTeam: TeamMailbox = TeamMailbox(DOMAIN_1, TeamMailboxName("sale"))
    val marketingTeam: TeamMailbox = TeamMailbox(DOMAIN_2, TeamMailboxName("marketing"))
    SMono.fromPublisher(testee.createTeamMailbox(saleTeam)).block()
    SMono.fromPublisher(testee.createTeamMailbox(marketingTeam)).block()

    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes()).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(saleTeam, marketingTeam)
  }

  @Test
  def listMembersShouldReturnCorrectRole(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asManager(ANDRE))).block()

    assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(TeamMailboxMember.asMember(BOB), TeamMailboxMember.asManager(ANDRE))
  }

  @Test
  def listMembersShouldNotReturnUserWithEmptyRight(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()

    mailboxManager.applyRightsCommand(TEAM_MAILBOX_MARKETING.mailboxPath, MailboxACL.command
      .key(MailboxACL.EntryKey.createUserEntryKey(BOB))
      .rights(MailboxACL.NO_RIGHTS)
      .asReplacement(),
      mailboxManager.createSystemSession(TEAM_MAILBOX_MARKETING.owner))

    assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava)
      .doesNotContain(TeamMailboxMember.asMember(BOB))
  }

  @Test
  def listMembersShouldNotReturnUserDoesNotHaveBasicTeamMailboxRights(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()

    mailboxManager.applyRightsCommand(TEAM_MAILBOX_MARKETING.mailboxPath, MailboxACL.command
      .key(MailboxACL.EntryKey.createUserEntryKey(BOB))
      .rights(MailboxACL.Right.Lookup)
      .asReplacement(),
      mailboxManager.createSystemSession(TEAM_MAILBOX_MARKETING.owner))

    assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava)
      .doesNotContain(TeamMailboxMember.asMember(BOB))
  }
}

object TeamMailboxCallbackNoop {
  val asSet: JavaSet[TeamMailboxCallback] = JavaSet.of(new TeamMailboxCallbackNoop)
}

class TeamMailboxCallbackNoop extends TeamMailboxCallback {
  override def teamMailboxAdded(teamMailbox: TeamMailbox): Publisher[Void] = SMono.empty

  override def teamMailboxRemoved(teamMailbox: TeamMailbox): Publisher[Void] = SMono.empty
}

class TeamMailboxRepositoryTest extends TeamMailboxRepositoryContract {
  override def testee: TeamMailboxRepository = teamMailboxRepositoryImpl

  override def mailboxManager: MailboxManager = inMemoryMailboxManager

  var teamMailboxRepositoryImpl: TeamMailboxRepositoryImpl = _
  var inMemoryMailboxManager: InMemoryMailboxManager = _
  var subscriptionManager: SubscriptionManager = _

  @BeforeEach
  def setUp(): Unit = {
    val resource: InMemoryIntegrationResources = InMemoryIntegrationResources.defaultResources()
    inMemoryMailboxManager = resource.getMailboxManager
    subscriptionManager = new StoreSubscriptionManager(resource.getMailboxManager.getMapperFactory, resource.getMailboxManager.getMapperFactory, resource.getMailboxManager.getEventBus)
    teamMailboxRepositoryImpl = new TeamMailboxRepositoryImpl(inMemoryMailboxManager, subscriptionManager, TeamMailboxCallbackNoop.asSet)
  }

  @Test
  def teamMailboxMigrationShouldWork(): Unit = {
    // GIVEN a team mailbox
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_MARKETING)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_MARKETING, TeamMailboxMember.asMember(BOB))).block()

    SMono.fromPublisher(new MailboxUsernameChangeTaskStep(inMemoryMailboxManager, subscriptionManager).changeUsername(BOB, ANDRE)).block()
    SMono.fromPublisher(new ACLUsernameChangeTaskStep(inMemoryMailboxManager, subscriptionManager).changeUsername(BOB, ANDRE)).block()

    SoftAssertions.assertSoftly(softly => {
      // Member of the team mailbox is changed
      softly.assertThat(SFlux(testee.listTeamMailboxes()).collectSeq().block().asJava).containsOnly(TEAM_MAILBOX_MARKETING)
      softly.assertThat(SFlux(testee.listMembers(TEAM_MAILBOX_MARKETING)).collectSeq().block().asJava).containsOnly(TeamMailboxMember.asMember(ANDRE))

      // Old member no longer see the team mailbox
      softly.assertThat(inMemoryMailboxManager.search(MailboxQuery.builder().matchesAllMailboxNames().build(),
            inMemoryMailboxManager.createSystemSession(BOB)).collectList()
          .block())
        .noneMatch(mailbox => mailbox.getMailbox.getNamespace.equals(TEAM_MAILBOX_NAMESPACE))

      // new user sees the team mailbox
      softly.assertThat(inMemoryMailboxManager.search(MailboxQuery.builder().matchesAllMailboxNames().build(),
            inMemoryMailboxManager.createSystemSession(ANDRE)).collectList()
          .block())
        .anyMatch(mailbox => mailbox.getMailbox.getNamespace.equals(TEAM_MAILBOX_NAMESPACE))
    })
  }
}
