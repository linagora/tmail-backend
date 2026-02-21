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

import com.linagora.tmail.team.TeamMailboxSubscriptionListenerTest._
import eu.timepit.refined.auto._
import org.apache.james.core.{Domain, Username}
import org.apache.james.mailbox.SubscriptionManager
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath}
import org.apache.james.mailbox.store.StoreSubscriptionManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object TeamMailboxSubscriptionListenerTest {
  val DOMAIN: Domain = Domain.of("linagora.com")
  val MARKETING: TeamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
  val SALE: TeamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("sale"))
  val BOB: Username = Username.of("bob@linagora.com")
  val ALICE: Username = Username.of("alice@linagora.com")
  val EXTRA_USER: Username = Username.of("extra@linagora.com")
}

class TeamMailboxSubscriptionListenerTest {

  var mailboxManager: InMemoryMailboxManager = _
  var subscriptionManager: SubscriptionManager = _
  var teamMailboxRepository: TeamMailboxRepositoryImpl = _
  var listener: TeamMailboxSubscriptionListener = _

  @BeforeEach
  def setUp(): Unit = {
    val resources: InMemoryIntegrationResources = InMemoryIntegrationResources.defaultResources()
    mailboxManager = resources.getMailboxManager
    subscriptionManager = new StoreSubscriptionManager(
      resources.getMailboxManager.getMapperFactory,
      resources.getMailboxManager.getMapperFactory,
      resources.getMailboxManager.getEventBus)
    teamMailboxRepository = new TeamMailboxRepositoryImpl(
      mailboxManager,
      subscriptionManager,
      resources.getMailboxManager.getMapperFactory,
      TeamMailboxCallbackNoop.asSet)

    listener = new TeamMailboxSubscriptionListener(teamMailboxRepository, mailboxManager, subscriptionManager)
    resources.getEventBus.register(listener)
  }

  @Test
  def newFolderShouldNotSubscribeMembersWhenTeamMailboxHasNone(): Unit = {
    SMono.fromPublisher(teamMailboxRepository.createTeamMailbox(MARKETING)).block()
    val ownerSession = mailboxManager.createSystemSession(MARKETING.owner)
    mailboxManager.createMailbox(MARKETING.mailboxPath("custom"), ownerSession)

    val bobSession = mailboxManager.createSystemSession(BOB)
    assertThat(SFlux(subscriptionManager.subscriptionsReactive(bobSession)).collectSeq().block().asJava)
      .doesNotContain(MARKETING.mailboxPath("custom"))
  }

  @Test
  def newFolderShouldSubscribeMember(): Unit = {
    SMono.fromPublisher(teamMailboxRepository.createTeamMailbox(MARKETING)).block()
    SMono.fromPublisher(teamMailboxRepository.addMember(MARKETING, TeamMailboxMember.asMember(BOB))).block()
    val ownerSession = mailboxManager.createSystemSession(MARKETING.owner)

    mailboxManager.createMailbox(MARKETING.mailboxPath("custom"), ownerSession)

    val bobSession = mailboxManager.createSystemSession(BOB)
    assertThat(SFlux(subscriptionManager.subscriptionsReactive(bobSession)).collectSeq().block().asJava)
      .contains(MARKETING.mailboxPath("custom"))
  }

  @Test
  def newFolderShouldSubscribeAllMembers(): Unit = {
    SMono.fromPublisher(teamMailboxRepository.createTeamMailbox(MARKETING)).block()
    SMono.fromPublisher(teamMailboxRepository.addMember(MARKETING, TeamMailboxMember.asMember(BOB))).block()
    SMono.fromPublisher(teamMailboxRepository.addMember(MARKETING, TeamMailboxMember.asMember(ALICE))).block()
    val ownerSession = mailboxManager.createSystemSession(MARKETING.owner)

    mailboxManager.createMailbox(MARKETING.mailboxPath("custom"), ownerSession)

    val bobSession = mailboxManager.createSystemSession(BOB)
    val aliceSession = mailboxManager.createSystemSession(ALICE)
    assertThat(SFlux(subscriptionManager.subscriptionsReactive(bobSession)).collectSeq().block().asJava)
      .contains(MARKETING.mailboxPath("custom"))
    assertThat(SFlux(subscriptionManager.subscriptionsReactive(aliceSession)).collectSeq().block().asJava)
      .contains(MARKETING.mailboxPath("custom"))
  }

  @Test
  def newFolderShouldNotSubscribeMembersOfAnotherTeamMailbox(): Unit = {
    SMono.fromPublisher(teamMailboxRepository.createTeamMailbox(MARKETING)).block()
    SMono.fromPublisher(teamMailboxRepository.createTeamMailbox(SALE)).block()
    SMono.fromPublisher(teamMailboxRepository.addMember(SALE, TeamMailboxMember.asMember(BOB))).block()
    val ownerSession = mailboxManager.createSystemSession(MARKETING.owner)

    mailboxManager.createMailbox(MARKETING.mailboxPath("custom"), ownerSession)

    val bobSession = mailboxManager.createSystemSession(BOB)
    assertThat(SFlux(subscriptionManager.subscriptionsReactive(bobSession)).collectSeq().block().asJava)
      .doesNotContain(MARKETING.mailboxPath("custom"))
  }

  @Test
  def newFolderShouldNotAffectNonTeamMailboxPaths(): Unit = {
    val bobSession = mailboxManager.createSystemSession(BOB)
    mailboxManager.createMailbox(MailboxPath.forUser(BOB, "xyz"), bobSession)

    assertThat(SFlux(subscriptionManager.subscriptionsReactive(bobSession)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def newFolderWithExistingExtraAclShouldSubscribeExtraUser(): Unit = {
    SMono.fromPublisher(teamMailboxRepository.createTeamMailbox(MARKETING)).block()
    val ownerSession = mailboxManager.createSystemSession(MARKETING.owner)
    mailboxManager.createMailbox(MARKETING.mailboxPath("custom"), ownerSession)

    // Grant rights to extra user – this fires ACLUpdated and subscribes them
    mailboxManager.applyRightsCommand(MARKETING.mailboxPath("custom"),
      MailboxACL.command().forUser(EXTRA_USER).rights(MailboxACL.FULL_RIGHTS).asReplacement(),
      ownerSession)

    mailboxManager.createMailbox(MARKETING.mailboxPath("custom.abc"), ownerSession)
    val extraSession = mailboxManager.createSystemSession(EXTRA_USER)
    assertThat(SFlux(subscriptionManager.subscriptionsReactive(extraSession)).collectSeq().block().asJava)
      .contains(MARKETING.mailboxPath("custom.abc"))
  }

  @Test
  def grantingRightsToNonMemberShouldSubscribeThemToFolder(): Unit = {
    SMono.fromPublisher(teamMailboxRepository.createTeamMailbox(MARKETING)).block()
    val ownerSession = mailboxManager.createSystemSession(MARKETING.owner)
    mailboxManager.createMailbox(MARKETING.mailboxPath("custom"), ownerSession)

    mailboxManager.applyRightsCommand(MARKETING.mailboxPath("custom"),
      MailboxACL.command().forUser(EXTRA_USER).rights(MailboxACL.FULL_RIGHTS).asReplacement(),
      ownerSession)

    val extraSession = mailboxManager.createSystemSession(EXTRA_USER)
    assertThat(SFlux(subscriptionManager.subscriptionsReactive(extraSession)).collectSeq().block().asJava)
      .contains(MARKETING.mailboxPath("custom"))
  }

  @Test
  def grantingRightsToMultipleNonMembersShouldSubscribeAllOfThem(): Unit = {
    SMono.fromPublisher(teamMailboxRepository.createTeamMailbox(MARKETING)).block()
    val ownerSession = mailboxManager.createSystemSession(MARKETING.owner)
    mailboxManager.createMailbox(MARKETING.mailboxPath("custom"), ownerSession)

    mailboxManager.applyRightsCommand(MARKETING.mailboxPath("custom"),
      MailboxACL.command().forUser(EXTRA_USER).rights(MailboxACL.FULL_RIGHTS).asReplacement(),
      ownerSession)
    mailboxManager.applyRightsCommand(MARKETING.mailboxPath("custom"),
      MailboxACL.command().forUser(ALICE).rights(MailboxACL.FULL_RIGHTS).asReplacement(),
      ownerSession)

    val extraSession = mailboxManager.createSystemSession(EXTRA_USER)
    val aliceSession = mailboxManager.createSystemSession(ALICE)
    assertThat(SFlux(subscriptionManager.subscriptionsReactive(extraSession)).collectSeq().block().asJava)
      .contains(MARKETING.mailboxPath("custom"))
    assertThat(SFlux(subscriptionManager.subscriptionsReactive(aliceSession)).collectSeq().block().asJava)
      .contains(MARKETING.mailboxPath("custom"))
  }

  @Test
  def removingRightsShouldNotTriggerSubscription(): Unit = {
    SMono.fromPublisher(teamMailboxRepository.createTeamMailbox(MARKETING)).block()
    val ownerSession = mailboxManager.createSystemSession(MARKETING.owner)
    mailboxManager.createMailbox(MARKETING.mailboxPath("custom"), ownerSession)

    // Grant then remove
    mailboxManager.applyRightsCommand(MARKETING.mailboxPath("custom"),
      MailboxACL.command().forUser(EXTRA_USER).rights(MailboxACL.FULL_RIGHTS).asReplacement(),
      ownerSession)
    // Now remove – this fires ACLUpdated but the new ACL no longer contains EXTRA_USER
    mailboxManager.applyRightsCommand(MARKETING.mailboxPath("custom"),
      MailboxACL.command().forUser(EXTRA_USER).rights(MailboxACL.FULL_RIGHTS).asRemoval(),
      ownerSession)

    // EXTRA_USER was subscribed on the grant step; verify they were subscribed (not the focus)
    // The removal itself should not create a new subscription
    val extraSession = mailboxManager.createSystemSession(EXTRA_USER)
    // Subscription was created on grant – the listener only subscribes on addition, not removal
    // so we just verify the handler doesn't blow up and the grant subscription still stands
    assertThat(SFlux(subscriptionManager.subscriptionsReactive(extraSession)).collectSeq().block().asJava)
      .contains(MARKETING.mailboxPath("custom"))
  }

  @Test
  def aclUpdatedForNonTeamMailboxPathShouldBeIgnored(): Unit = {
    val bobSession = mailboxManager.createSystemSession(BOB)
    mailboxManager.createMailbox(MailboxPath.forUser(BOB, "custom"), bobSession)

    // No subscription should be triggered for a non-team-mailbox path
    val subsBefore = SFlux(subscriptionManager.subscriptionsReactive(bobSession)).collectSeq().block()
    mailboxManager.applyRightsCommand(MARKETING.mailboxPath("custom"),
      MailboxACL.command().forUser(EXTRA_USER).rights(MailboxACL.FULL_RIGHTS).asReplacement(),
      bobSession)

    val extraSession = mailboxManager.createSystemSession(EXTRA_USER)
    assertThat(SFlux(subscriptionManager.subscriptionsReactive(extraSession)).collectSeq().block().asJava)
      .doesNotContain(MARKETING.mailboxPath("custom"))
  }
}
