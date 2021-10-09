package com.linagora.tmail.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;

import reactor.core.publisher.Mono;

class TMailValidRcptHandlerTest {
    public static final Domain DOMAIN = Domain.of("linagora.com");
    public static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    public static final Username BOB_ALIAS = Username.fromLocalPartWithDomain("bob-alias", DOMAIN);
    public static final Username ADDRESS_ALIAS = Username.fromLocalPartWithDomain("address-alias", DOMAIN);
    public static final Username GROUP = Username.fromLocalPartWithDomain("group", DOMAIN);

    private TMailValidRcptHandler testee;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources integrationResources = InMemoryIntegrationResources.defaultResources();
        TeamMailboxRepositoryImpl teamMailboxRepository = new TeamMailboxRepositoryImpl(integrationResources.getMailboxManager(), integrationResources.getMailboxManager());

        MemoryDomainList domainList = new MemoryDomainList(mock(DNSService.class));
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(DOMAIN);

        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(BOB, "12345");

        MemoryRecipientRewriteTable rrt = new MemoryRecipientRewriteTable();
        rrt.setUsersRepository(usersRepository);
        rrt.setDomainList(domainList);
        rrt.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
        rrt.addAliasMapping(MappingSource.fromUser(BOB_ALIAS), BOB.asString());
        rrt.addGroupMapping(MappingSource.fromUser(GROUP), BOB.asString());
        rrt.addAddressMapping(MappingSource.fromUser(ADDRESS_ALIAS), BOB.asString());

        TeamMailbox teamMailboxOption = TeamMailbox.asTeamMailbox(new MailAddress("sales@linagora.com")).get();
        Mono.from(teamMailboxRepository.createTeamMailbox(teamMailboxOption)).block();
        Mono.from(teamMailboxRepository.addMember(teamMailboxOption, BOB)).block();

        testee = new TMailValidRcptHandler(usersRepository, rrt, domainList, teamMailboxRepository);
    }


    @ParameterizedTest
    @ValueSource(strings = {
        "bob@linagora.com",
        "bob-alias@linagora.com",
        "group@linagora.com",
        "address-alias@linagora.com",
        "sales@linagora.com"
    })
    void shouldHandeLocalResources(String address) throws Exception {
        assertThat(testee.isValidRecipient(mock(SMTPSession.class), new MailAddress(address)))
            .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "notFound@linagora.com",
        "bob@notFound.com",
        "not.found@linagora.com"
    })
    void shouldNotHandeRemoteResources(String address) throws Exception {
        assertThat(testee.isValidRecipient(mock(SMTPSession.class), new MailAddress(address)))
            .isFalse();
    }
}