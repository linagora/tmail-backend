package com.linagora.tmail.team

import com.linagora.tmail.team.TeamMailboxRepositoryContract.{ANDRE, BOB, TEAM_MAILBOX, TEAM_MAILBOX_2, TEAM_MAILBOX_USER, TEAM_MAILBOX_USERNAME, TEAM_MAILBOX_USERNAME_2, TEAM_MAILBOX_USER_2}
import eu.timepit.refined.auto._
import org.apache.james.core.{Domain, Username}
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.model.MailboxACL
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.{SFlux, SMono}

import java.util
import scala.jdk.CollectionConverters._

object TeamMailboxRepositoryContract {
  val TEAM_MAILBOX_USER: Domain = Domain.of("linagora.com")
  val TEAM_MAILBOX_USER_2: Domain = Domain.of("linagora2.com")
  val TEAM_MAILBOX_USERNAME: Username = Username.fromLocalPartWithDomain("team-mailbox", TEAM_MAILBOX_USER)
  val TEAM_MAILBOX_USERNAME_2: Username = Username.fromLocalPartWithDomain("team-mailbox", TEAM_MAILBOX_USER_2)
  val TEAM_MAILBOX: TeamMailbox = TeamMailbox(TEAM_MAILBOX_USER, TeamMailboxName("marketing"))
  val TEAM_MAILBOX_2: TeamMailbox = TeamMailbox(TEAM_MAILBOX_USER, TeamMailboxName("sale"))
  val BOB: Username = Username.of("bob@linagora.com")
  val ANDRE: Username = Username.of("andre@linagora.com")
}

trait TeamMailboxRepositoryContract {

  def testee: TeamMailboxRepository

  def mailboxManager: MailboxManager

  @Test
  def createTeamMailboxShouldStoreThreeAssignMailboxes(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()
    val session: MailboxSession = mailboxManager.createSystemSession(TEAM_MAILBOX_USERNAME)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SMono.fromPublisher(mailboxManager.mailboxExists(TEAM_MAILBOX.mailboxPath, session))
        .block())
        .isTrue
      softly.assertThat(SMono.fromPublisher(mailboxManager.mailboxExists(TEAM_MAILBOX.inboxPath, session))
        .block())
        .isTrue
      softly.assertThat(SMono.fromPublisher(mailboxManager.mailboxExists(TEAM_MAILBOX.sentPath, session))
        .block())
        .isTrue
    })
  }

  @Test
  def existsShouldReturnTrueAfterCreate(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()

    assertThat(SMono.fromPublisher(testee.exists(TEAM_MAILBOX)).block())
      .isTrue
  }

  @Test
  def existsShouldReturnFalseByDefault(): Unit = {
    assertThat(SMono.fromPublisher(testee.exists(TEAM_MAILBOX)).block())
      .isFalse
  }

  @Test
  def createTeamMailboxShouldNotThrowWhenAssignMailboxExists(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()

    assertThatCode(() => SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def createMailboxWithSamePathShouldFailWhenTeamMailboxExists(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()
    val session: MailboxSession = mailboxManager.createSystemSession(TEAM_MAILBOX_USERNAME)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThatThrownBy(() => mailboxManager.createMailbox(TEAM_MAILBOX.mailboxPath, session))
        .hasMessageContaining(s"${TEAM_MAILBOX.mailboxPath.toString} already exists.")

      softly.assertThatThrownBy(() => mailboxManager.createMailbox(TEAM_MAILBOX.inboxPath, session))
        .hasMessageContaining(s"${TEAM_MAILBOX.inboxPath.toString} already exists.")

      softly.assertThatThrownBy(() => mailboxManager.createMailbox(TEAM_MAILBOX.sentPath, session))
        .hasMessageContaining(s"${TEAM_MAILBOX.sentPath.toString} already exists.")
    })
  }

  @Test
  def deleteTeamMailboxShouldRemoveAllAssignedMailboxes(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()
    SMono.fromPublisher(testee.deleteTeamMailbox(TEAM_MAILBOX)).block()
    val session: MailboxSession = mailboxManager.createSystemSession(TEAM_MAILBOX_USERNAME)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SMono.fromPublisher(mailboxManager.mailboxExists(TEAM_MAILBOX.mailboxPath, session))
        .block())
        .isFalse
      softly.assertThat(SMono.fromPublisher(mailboxManager.mailboxExists(TEAM_MAILBOX.inboxPath, session))
        .block())
        .isFalse
      softly.assertThat(SMono.fromPublisher(mailboxManager.mailboxExists(TEAM_MAILBOX.sentPath, session))
        .block())
        .isFalse
    })
  }

  @Test
  def deleteTeamMailboxShouldNotRemoveOtherTeamMailboxes(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_2)).block()
    SMono.fromPublisher(testee.deleteTeamMailbox(TEAM_MAILBOX)).block()
    val session: MailboxSession = mailboxManager.createSystemSession(TEAM_MAILBOX_USERNAME_2)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(SMono.fromPublisher(mailboxManager.mailboxExists(TEAM_MAILBOX_2.mailboxPath, session))
        .block())
        .isTrue
      softly.assertThat(SMono.fromPublisher(mailboxManager.mailboxExists(TEAM_MAILBOX_2.inboxPath, session))
        .block())
        .isTrue
      softly.assertThat(SMono.fromPublisher(mailboxManager.mailboxExists(TEAM_MAILBOX_2.sentPath, session))
        .block())
        .isTrue
    })
  }

  @Test
  def deleteTeamMailboxShouldNotThrowWhenAssignMailboxDoesNotExists(): Unit = {
    assertThatCode(() => SMono.fromPublisher(testee.deleteTeamMailbox(TEAM_MAILBOX)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def addMemberShouldThrowWhenTeamMailboxDoesNotExists(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.addMember(TEAM_MAILBOX, BOB)).block())
      .isInstanceOf(classOf[TeamMailboxNotFoundException])
  }

  @Test
  def addMemberShouldAddImplicitRights(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX, BOB)).block()
    val bobSession: MailboxSession = mailboxManager.createSystemSession(BOB)
    val entriesRights: util.Map[MailboxACL.EntryKey, MailboxACL.Rfc4314Rights] = mailboxManager.listRights(TEAM_MAILBOX.mailboxPath, bobSession).getEntries

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(entriesRights)
        .hasSize(1)
      softly.assertThat(entriesRights.asScala.head._2.toString)
        .isEqualTo("ilprstw")
    })
  }

  @Test
  def removeMemberShouldThrowWhenTeamMailboxDoesNotExists(): Unit = {
    assertThatThrownBy(() => SMono.fromPublisher(testee.removeMember(TEAM_MAILBOX, BOB)).block())
      .isInstanceOf(classOf[TeamMailboxNotFoundException])
  }

  @Test
  def removeMemberShouldThrowWhenMemberNotInTeamMailbox(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()

    assertThatThrownBy(() => SMono.fromPublisher(testee.removeMember(TEAM_MAILBOX, BOB)).block())
      .isInstanceOf(classOf[TeamMailboxNotFoundException])
  }

  @Test
  def removeMemberShouldRevokeMemberRightsFromTeamMailbox(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX, BOB)).block()
    SMono.fromPublisher(testee.removeMember(TEAM_MAILBOX, BOB)).block()

    assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX)).collectSeq().block().asJava)
      .doesNotContain(BOB)
  }

  @Test
  def removeMemberShouldNotRevokeOtherMembers(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX, BOB)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX, ANDRE)).block()
    SMono.fromPublisher(testee.removeMember(TEAM_MAILBOX, BOB)).block()

    assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX)).collectSeq().block().asJava)
      .contains(ANDRE)
  }

  @Test
  def listMemberShouldReturnEmptyWhenDefault(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()

    assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def listMemberShouldReturnListMembersHaveRightsWhenSingleMember(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX, BOB)).block()

    assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(BOB)
  }

  @Test
  def listMemberShouldReturnListMembersHaveRightsWhenSeveralMembers(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX, BOB)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX, ANDRE)).block()

    assertThat(SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(BOB, ANDRE)
  }

  @Test
  def listMemberShouldThrowWhenTeamMailboxDoesNotExists(): Unit = {
    assertThatThrownBy(()=>SFlux.fromPublisher(testee.listMembers(TEAM_MAILBOX)).collectSeq().block().asJava)
      .isInstanceOf(classOf[TeamMailboxNotFoundException])
  }

  @Test
  def listTeamMailboxesByUserShouldReturnEmptyByDefault(): Unit = {
    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(BOB)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def listTeamMailboxesByUserShouldReturnTeamMailboxesWhichUserIsMemberOf(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX, BOB)).block()

    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(BOB)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(TEAM_MAILBOX)
  }

  @Test
  def listTeamMailboxesByUserShouldNotReturnTeamMailboxesWhichUserIsNotMemberOf(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX, ANDRE)).block()

    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(BOB)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def userCanBeMemberOfSeveralTeamMailboxes(): Unit = {
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX, BOB)).block()
    SMono.fromPublisher(testee.createTeamMailbox(TEAM_MAILBOX_2)).block()
    SMono.fromPublisher(testee.addMember(TEAM_MAILBOX_2, BOB)).block()

    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(BOB)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(TEAM_MAILBOX, TEAM_MAILBOX_2)
  }

  @Test
  def listTeamMailboxesByDomainShouldReturnEmptyByDefault(): Unit = {
    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(TEAM_MAILBOX_USER)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def listTeamMailboxesByDomainShouldReturnStoredEntriesWhenSingle(): Unit = {
    val saleTeam: TeamMailbox = TeamMailbox(TEAM_MAILBOX_USER, TeamMailboxName("sale"))
    SMono.fromPublisher(testee.createTeamMailbox(saleTeam)).block()

    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(TEAM_MAILBOX_USER)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(saleTeam)
  }

  @Test
  def listTeamMailboxesByDomainShouldReturnStoredEntriesWhenMultiple(): Unit = {
    val saleTeam: TeamMailbox = TeamMailbox(TEAM_MAILBOX_USER, TeamMailboxName("sale"))
    val marketingTeam: TeamMailbox = TeamMailbox(TEAM_MAILBOX_USER, TeamMailboxName("marketing"))
    SMono.fromPublisher(testee.createTeamMailbox(saleTeam)).block()
    SMono.fromPublisher(testee.createTeamMailbox(marketingTeam)).block()

    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(TEAM_MAILBOX_USER)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(saleTeam, marketingTeam)
  }

  @Test
  def listTeamMailboxesByDomainShouldNotReturnUnRelatedEntries(): Unit = {
    val saleTeam: TeamMailbox = TeamMailbox(TEAM_MAILBOX_USER, TeamMailboxName("sale"))
    val marketingTeam: TeamMailbox = TeamMailbox(TEAM_MAILBOX_USER_2, TeamMailboxName("marketing"))
    SMono.fromPublisher(testee.createTeamMailbox(saleTeam)).block()
    SMono.fromPublisher(testee.createTeamMailbox(marketingTeam)).block()

    assertThat(SFlux.fromPublisher(testee.listTeamMailboxes(TEAM_MAILBOX_USER)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(saleTeam)
  }

}

class TeamMailboxRepositoryTest extends TeamMailboxRepositoryContract {
  override def testee: TeamMailboxRepository = teamMailboxRepositoryImpl

  override def mailboxManager: MailboxManager = inMemoryMailboxManager

  var teamMailboxRepositoryImpl: TeamMailboxRepositoryImpl = _
  var inMemoryMailboxManager: InMemoryMailboxManager = _

  @BeforeEach
  def setUp(): Unit = {
    val resource: InMemoryIntegrationResources = InMemoryIntegrationResources.defaultResources()
    inMemoryMailboxManager = resource.getMailboxManager
    teamMailboxRepositoryImpl = new TeamMailboxRepositoryImpl(inMemoryMailboxManager)
  }
}
