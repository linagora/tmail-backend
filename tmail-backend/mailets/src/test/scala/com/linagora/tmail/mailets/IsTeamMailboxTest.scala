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

package com.linagora.tmail.mailets

import com.google.common.collect.ImmutableList
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxCallbackNoop, TeamMailboxRepository, TeamMailboxRepositoryImpl}
import org.apache.james.core.MailAddress
import org.apache.james.mailbox.MailboxManager
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.store.StoreSubscriptionManager
import org.apache.mailet.base.test.FakeMail
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

class IsTeamMailboxTest {
  private var testee: IsTeamMailbox = null
  private var mailboxManager: MailboxManager = null
  private var teamMailboxRepository: TeamMailboxRepository = null

  @BeforeEach
  def setup(): Unit = {

    val resources = InMemoryIntegrationResources.defaultResources()
    mailboxManager = resources.getMailboxManager
    val subscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager.getMapperFactory,
      resources.getMailboxManager.getMapperFactory, resources.getMailboxManager.getEventBus)

    teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager, subscriptionManager, resources.getMailboxManager.getMapperFactory, TeamMailboxCallbackNoop.asSet)

    testee = new IsTeamMailbox(teamMailboxRepository)
  }

  @Test
  def shouldNotMatchRegularAddresses(): Unit = {
    val address = new MailAddress("user1@domain.tld")
    val mail = FakeMail.defaultFakeMail()
    mail.setRecipients(ImmutableList.of(address))

    val expected = testee.`match`(mail)

    assertThat(expected).isEmpty()
  }

  @Test
  def shouldMatchTeamMailboxes(): Unit = {
    val address = new MailAddress("user1@domain.tld")

    SMono(teamMailboxRepository.createTeamMailbox(TeamMailbox.asTeamMailbox(address).get)).block()

    val mail = FakeMail.defaultFakeMail()
    mail.setRecipients(ImmutableList.of(address))

    val expected = testee.`match`(mail)

    assertThat(expected).containsExactly(address)
  }
}
