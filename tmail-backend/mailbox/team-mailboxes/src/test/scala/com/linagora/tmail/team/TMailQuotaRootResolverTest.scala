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

import java.util.Optional

import eu.timepit.refined.auto._
import org.apache.james.core.{Domain, Username}
import org.apache.james.mailbox.MailboxManager
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.model.{MailboxPath, QuotaRoot}
import org.apache.james.mailbox.store.StoreSubscriptionManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

class TMailQuotaRootResolverTest {
  private var testee: TMailQuotaRootResolver = _
  private var teamMailboxRepository: TeamMailboxRepository = _
  private var mailboxManager: MailboxManager = _

  private val teamMailbox = TeamMailbox(Domain.of("linagora.com"), TeamMailboxName("sales"))
  private val username = Username.of("bob@linagora.com")
  private val bobQuotaRoot = QuotaRoot.quotaRoot("#private&bob@linagora.com", Optional.of(Domain.of("linagora.com")))

  @BeforeEach
  def setUp(): Unit = {
    val resources = InMemoryIntegrationResources.defaultResources()
    mailboxManager = resources.getMailboxManager
    val subscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager.getMapperFactory, resources.getMailboxManager.getMapperFactory, resources.getMailboxManager.getEventBus)
    teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager, subscriptionManager, resources.getMailboxManager.getMapperFactory, TeamMailboxCallbackNoop.asSet)
    testee = new TMailQuotaRootResolver(mailboxManager, resources.getMailboxManager.getMapperFactory, teamMailboxRepository)
  }

  @Test
  def forMailAddressShouldDefaultToUserQuotaRoot(): Unit = {
    assertThat(testee.forMailAddress(Username.of("bob@linagora.com")))
      .isEqualTo(bobQuotaRoot)
  }

  @Test
  def forMailAddressShouldRecognizeTeamMailboxes(): Unit = {
    SMono(teamMailboxRepository.createTeamMailbox(teamMailbox)).block()
    assertThat(testee.forMailAddress(Username.of("sales@linagora.com")))
      .isEqualTo(teamMailbox.quotaRoot)
  }

  @Test
  def getQuotaRootShouldRecognizeTeamMailbox(): Unit = {
    assertThat(testee.getQuotaRoot(teamMailbox.mailboxPath))
      .isEqualTo(teamMailbox.quotaRoot)
  }

  @Test
  def getQuotaRootShouldRecognizeTeamMailboxWhenInbox(): Unit = {
    assertThat(testee.getQuotaRoot(teamMailbox.inboxPath))
      .isEqualTo(teamMailbox.quotaRoot)
  }

  @Test
  def getQuotaRootShouldRecognizeUserMailboxes(): Unit = {
    assertThat(testee.getQuotaRoot(MailboxPath.forUser(username, "aMailbox")))
      .isEqualTo(bobQuotaRoot)
  }

  @Test
  def getQuotaRootShouldRecognizeTeamMailboxByMailboxId(): Unit = {
    SMono(teamMailboxRepository.createTeamMailbox(teamMailbox)).block()

    val mailboxId = mailboxManager.getMailbox(teamMailbox.inboxPath, mailboxManager.createSystemSession(teamMailbox.inboxPath.getUser)).getId

    assertThat(testee.getQuotaRoot(mailboxId))
      .isEqualTo(teamMailbox.quotaRoot)
  }

  @Test
  def getQuotaRootShouldRecognizeUserByMailboxId(): Unit = {
    val mailboxId = mailboxManager.createMailbox(MailboxPath.forUser(username, "aBox"), mailboxManager.createSystemSession(username)).get()

    assertThat(testee.getQuotaRoot(mailboxId))
      .isEqualTo(bobQuotaRoot)
  }

  @Test
  def associatedUsernameShouldReturnTeamMailboxAddress(): Unit = {
    assertThat(testee.associatedUsername(teamMailbox.quotaRoot))
      .isEqualTo(Username.of(teamMailbox.asMailAddress.asString()))
  }

  @Test
  def associatedUsernameShouldReturnUserAddress(): Unit = {
    assertThat(testee.associatedUsername(bobQuotaRoot))
      .isEqualTo(username)
  }

  @Test
  def retrieveAssociatedMailboxesShouldListAllTeamMailboxSubMailboxes(): Unit = {
    SMono(teamMailboxRepository.createTeamMailbox(teamMailbox)).block()

    assertThat(testee.retrieveAssociatedMailboxes(teamMailbox.quotaRoot, mailboxManager.createSystemSession(Username.of("any")))
        .map(_.generateAssociatedPath())
        .collectList()
        .block())
      .containsOnly(teamMailbox.mailboxPath, teamMailbox.inboxPath, teamMailbox.sentPath,
        teamMailbox.mailboxPath("Drafts"), teamMailbox.mailboxPath("Outbox"), teamMailbox.mailboxPath("Trash"),
        teamMailbox.mailboxPath("Templates"))
  }

  @Test
  def retrieveAssociatedMailboxesShouldReturnEmptyWhenTeamMailboxDoesNotExists(): Unit = {
    assertThat(testee.retrieveAssociatedMailboxes(teamMailbox.quotaRoot, mailboxManager.createSystemSession(Username.of("any")))
        .map(_.generateAssociatedPath())
        .collectList()
        .block())
      .isEmpty()
  }
}
