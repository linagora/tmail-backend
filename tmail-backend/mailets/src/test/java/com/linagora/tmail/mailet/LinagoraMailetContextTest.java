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

package com.linagora.tmail.mailet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.AbstractDomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mailetcontainer.api.LocalResources;
import org.apache.james.mailetcontainer.impl.JamesMailetContext;
import org.apache.james.mailetcontainer.impl.JamesMailetContextContract;
import org.apache.james.mailetcontainer.impl.LocalResourcesImpl;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxCallbackNoop;
import com.linagora.tmail.team.TeamMailboxRepository;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;

import reactor.core.publisher.Mono;

public class LinagoraMailetContextTest implements JamesMailetContextContract {
    private static final TeamMailbox TEAM_MAILBOX = TeamMailbox.fromJava(DOMAIN_COM, "makerting").get();

    @Override
    public AbstractDomainList domainList() {
        return domainList;
    }

    @Override
    public UsersRepository usersRepository() {
        return usersRepository;
    }

    @Override
    public JamesMailetContext testee() {
        return testee;
    }

    @Override
    public MailAddress mailAddress() {
        return mailAddress;
    }

    @Override
    public MailQueue spoolMailQueue() {
        return spoolMailQueue;
    }

    @Override
    public AbstractRecipientRewriteTable recipientRewriteTable() {
        return recipientRewriteTable;
    }

    private MemoryDomainList domainList;
    private MemoryUsersRepository usersRepository;
    private JamesMailetContext testee;
    private MailAddress mailAddress;
    private MailQueue spoolMailQueue;
    private MemoryRecipientRewriteTable recipientRewriteTable;
    private TeamMailboxRepository teamMailboxRepository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        DNSService dnsService = null;
        domainList = spy(new MemoryDomainList(dnsService));
        domainList.configure(DomainListConfiguration.DEFAULT);

        usersRepository = spy(MemoryUsersRepository.withVirtualHosting(domainList));
        recipientRewriteTable = spy(new MemoryRecipientRewriteTable());
        recipientRewriteTable.configure(new BaseHierarchicalConfiguration());
        MailQueueFactory<MailQueue> mailQueueFactory = mock(MailQueueFactory.class);
        spoolMailQueue = mock(MailQueue.class);
        when(mailQueueFactory.createQueue(MailQueueFactory.SPOOL)).thenReturn(spoolMailQueue);

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager().getMapperFactory(),
                resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getEventBus());

        teamMailboxRepository = new TeamMailboxRepositoryImpl(resources.getMailboxManager(), subscriptionManager, java.util.Set.of(new TeamMailboxCallbackNoop()));

        LocalResources localResources = new LocalResourcesImpl(usersRepository, domainList, recipientRewriteTable);
        mailAddress = new MailAddress(USERMAIL.asString());

        TmailLocalResources tmailLocalResources = new TmailLocalResources(localResources, teamMailboxRepository);

        testee = new JamesMailetContext(dnsService, domainList, tmailLocalResources, mailQueueFactory);
        testee.configure(new BaseHierarchicalConfiguration());
    }

    @Test
    void isLocalEmailShouldReturnFalseWhenUserDoesNotExistsAndTeamMailboxDoesNotExists() {
        assertThat(testee.isLocalEmail(TEAM_MAILBOX.asMailAddress())).isFalse();
    }

    @Test
    void isLocalEmailShouldReturnTrueWhenUserDoesNotExistsAndTeamMailboxExists() {
        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
        assertThat(testee.isLocalEmail(TEAM_MAILBOX.asMailAddress())).isTrue();
    }

    @Test
    void localRecipientsShouldReturnAddressWhenTeamMailboxExists() {
        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
        assertThat(testee.localRecipients(ImmutableList.of(TEAM_MAILBOX.asMailAddress())))
            .containsExactlyInAnyOrder(TEAM_MAILBOX.asMailAddress());
    }

    @Test
    void localRecipientsShouldReturnOnlyExistingUsersWhenTeamMailboxesDoNotExists() throws Exception {
        domainList().addDomain(DOMAIN_COM);
        usersRepository().addUser(USERMAIL, PASSWORD);
        ImmutableList<MailAddress> mailAddresses = ImmutableList.of(TEAM_MAILBOX.asMailAddress(), mailAddress);
        assertThat(testee.localRecipients(mailAddresses))
            .containsExactlyInAnyOrder(mailAddress);
    }

    @Test
    void localRecipientsShouldReturnBothExistingUsersAndExistingTeamMailboxes() throws Exception {
        domainList().addDomain(DOMAIN_COM);
        usersRepository().addUser(USERMAIL, PASSWORD);
        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

        ImmutableList<MailAddress> mailAddresses = ImmutableList.of(TEAM_MAILBOX.asMailAddress(), mailAddress);
        assertThat(testee.localRecipients(mailAddresses))
            .containsExactlyInAnyOrder(TEAM_MAILBOX.asMailAddress(), mailAddress);
    }

    @Test
    void localRecipientsShouldReturnOnlyExistingTeamMailboxesWhenUsersDoNotExists() {
        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

        ImmutableList<MailAddress> mailAddresses = ImmutableList.of(TEAM_MAILBOX.asMailAddress(), mailAddress);
        assertThat(testee.localRecipients(mailAddresses))
            .containsExactlyInAnyOrder(TEAM_MAILBOX.asMailAddress());
    }
}
