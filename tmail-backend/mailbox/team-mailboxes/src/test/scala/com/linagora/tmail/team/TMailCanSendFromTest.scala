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

import eu.timepit.refined.auto._
import org.apache.james.core.{Domain, MailAddress, Username}
import org.apache.james.dnsservice.api.DNSService
import org.apache.james.domainlist.lib.DomainListConfiguration
import org.apache.james.domainlist.memory.MemoryDomainList
import org.apache.james.mailbox.MailboxManager
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.model.MailboxACL
import org.apache.james.mailbox.store.StoreSubscriptionManager
import org.apache.james.rrt.api.{CanSendFrom, RecipientRewriteTableConfiguration}
import org.apache.james.rrt.lib.{AliasReverseResolverImpl, CanSendFromContract, Mapping, MappingSource}
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable
import org.apache.james.user.memory.MemoryUsersRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.Mockito.mock
import reactor.core.publisher.Flux
import reactor.core.scala.publisher.SMono

class TMailCanSendFromTest extends CanSendFromContract {
  var testee: CanSendFrom = _
  var rrt: MemoryRecipientRewriteTable = _
  var teamMailboxRepository: TeamMailboxRepository = _
  var mailboxManager: MailboxManager = _

  @BeforeEach
  def setUp(): Unit = {
    val integrationResources = InMemoryIntegrationResources.defaultResources
    mailboxManager = integrationResources.getMailboxManager
    val subscriptionManager = new StoreSubscriptionManager(integrationResources.getMailboxManager.getMapperFactory,
      integrationResources.getMailboxManager.getMapperFactory,
      integrationResources.getMailboxManager.getEventBus)

    teamMailboxRepository = new TeamMailboxRepositoryImpl(integrationResources.getMailboxManager, subscriptionManager, integrationResources.getMailboxManager.getMapperFactory, TeamMailboxCallbackNoop.asSet)

    val domainList = new MemoryDomainList(mock(classOf[DNSService]))
    domainList.configure(DomainListConfiguration.DEFAULT)
    domainList.addDomain(CanSendFromContract.DOMAIN)
    domainList.addDomain(CanSendFromContract.OTHER_DOMAIN)

    val usersRepository = MemoryUsersRepository.withVirtualHosting(domainList)

    rrt = new MemoryRecipientRewriteTable
    rrt.setUsersRepository(usersRepository)
    rrt.setDomainList(domainList)
    rrt.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED)

    testee = new TMailCanSendFrom(new AliasReverseResolverImpl(rrt), teamMailboxRepository, mailboxManager)
  }

  override def canSendFrom: CanSendFrom = testee

  override def addAliasMapping(alias: Username, user: Username): Unit = rrt.addAliasMapping(MappingSource.fromUser(alias.getLocalPart, alias.getDomainPart.get), user.asString)

  override def addDomainMapping(alias: Domain, domain: Domain, mappingType: Mapping.Type): Unit = {
    mappingType match {
      case Mapping.Type.Domain =>
        rrt.addDomainMapping(MappingSource.fromDomain(alias), domain)

      case Mapping.Type.DomainAlias =>
        rrt.addDomainAliasMapping(MappingSource.fromDomain(alias), domain)

      case _ =>
    }
  }

  override def addGroupMapping(group: String, user: Username): Unit = rrt.addGroupMapping(MappingSource.fromUser(Username.of(group)), user.asString)

  @Test
  def teamMailboxMemberCanSend(): Unit = {
    val teamMailbox = TeamMailbox(Domain.of("domain.tld"), TeamMailboxName("marketing"))
    SMono(teamMailboxRepository.createTeamMailbox(teamMailbox)).block()
    SMono(teamMailboxRepository.addMember(teamMailbox,  TeamMailboxMember.asMember(Username.of("bob@domain.tld")))).block()

    assertThat(testee.userCanSendFrom(Username.of("bob@domain.tld"), Username.of("marketing@domain.tld")))
      .isTrue
  }

  @Test
  def nonMembersCannotSend(): Unit = {
    val teamMailbox = TeamMailbox(Domain.of("domain.tld"), TeamMailboxName("marketing"))
    SMono(teamMailboxRepository.createTeamMailbox(teamMailbox)).block()

    assertThat(testee.userCanSendFrom(Username.of("bob@domain.tld"), Username.of("marketing@domain.tld")))
      .isFalse
  }

  @Test
  def allValidFromAddressesForUserShouldListTeamMailboxAlias(): Unit = {
    val teamMailbox = TeamMailbox(Domain.of("domain.tld"), TeamMailboxName("marketing"))
    SMono(teamMailboxRepository.createTeamMailbox(teamMailbox)).block()
    SMono(teamMailboxRepository.addMember(teamMailbox,  TeamMailboxMember.asMember(Username.of("bob@domain.tld")))) .block()

    assertThat(Flux.from(testee.allValidFromAddressesForUser(Username.of("bob@domain.tld")))
      .collectList().block())
      .containsOnly(new MailAddress("marketing@domain.tld"), new MailAddress("bob@domain.tld"))
  }

  @Test
  def extraSenderCanSendAsTeamMailbox(): Unit = {
    val teamMailbox = TeamMailbox(Domain.of("domain.tld"), TeamMailboxName("marketing"))
    SMono(teamMailboxRepository.createTeamMailbox(teamMailbox)).block()

    val session = mailboxManager.createSystemSession(teamMailbox.admin)
    mailboxManager.applyRightsCommand(
      teamMailbox.mailboxPath,
      MailboxACL.command().forUser(Username.of("bob@domain.tld")).rights(MailboxACL.Right.Post).asAddition(),
      session)

    assertThat(testee.userCanSendFrom(Username.of("bob@domain.tld"), Username.of("marketing@domain.tld")))
      .isTrue
  }

  @Test
  def extraSenderLosesRightAfterRemoval(): Unit = {
    val teamMailbox = TeamMailbox(Domain.of("domain.tld"), TeamMailboxName("marketing"))
    SMono(teamMailboxRepository.createTeamMailbox(teamMailbox)).block()

    val session = mailboxManager.createSystemSession(teamMailbox.admin)
    mailboxManager.applyRightsCommand(
      teamMailbox.mailboxPath,
      MailboxACL.command().forUser(Username.of("bob@domain.tld")).rights(MailboxACL.Right.Post).asAddition(),
      session)

    mailboxManager.applyRightsCommand(
      teamMailbox.mailboxPath,
      MailboxACL.command().forUser(Username.of("bob@domain.tld")).rights(MailboxACL.Right.Post).asRemoval(),
      session)

    assertThat(testee.userCanSendFrom(Username.of("bob@domain.tld"), Username.of("marketing@domain.tld")))
      .isFalse
  }
}
