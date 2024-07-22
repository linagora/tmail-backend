package com.linagora.tmail.smtp;

import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.ldap.DockerLdapSingleton;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxCallbackNoop;
import com.linagora.tmail.team.TeamMailboxMember;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;
import reactor.core.publisher.Mono;

class TMailWithMailingListValidRcptHandlerTest {
    static LdapGenericContainer ldapContainer = DockerLdapSingleton.ldapContainer;

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    public static final Domain DOMAIN = Domain.of("linagora.com");
    public static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    public static final Username BOB_ALIAS = Username.fromLocalPartWithDomain("bob-alias", DOMAIN);
    public static final Username ADDRESS_ALIAS = Username.fromLocalPartWithDomain("address-alias", DOMAIN);
    public static final Username GROUP = Username.fromLocalPartWithDomain("group", DOMAIN);

    private TMailWithMailingListValidRcptHandler testee;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources integrationResources = InMemoryIntegrationResources.defaultResources();
        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(integrationResources.getMailboxManager().getMapperFactory(),
                integrationResources.getMailboxManager().getMapperFactory(), integrationResources.getMailboxManager().getEventBus());

        TeamMailboxRepositoryImpl teamMailboxRepository = new TeamMailboxRepositoryImpl(integrationResources.getMailboxManager(), subscriptionManager, java.util.Set.of(new TeamMailboxCallbackNoop()));

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
        Mono.from(teamMailboxRepository.addMember(teamMailboxOption, TeamMailboxMember.asMember(BOB))).block();

        testee = new TMailWithMailingListValidRcptHandler(usersRepository, rrt, domainList, teamMailboxRepository,
            LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));

        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty("baseDN", "ou=lists,dc=james,dc=org");
        configuration.addProperty("groupObjectClass", "groupofnames");
        configuration.addProperty("mailAttributeForGroups", "description");
        testee.init(configuration);
    }


    @ParameterizedTest
    @ValueSource(strings = {
        "bob@linagora.com",
        "bob-alias@linagora.com",
        "group@linagora.com",
        "address-alias@linagora.com",
        "sales@linagora.com",
        "mygroup@lists.james.org"
    })
    void shouldHandeLocalResources(String address) throws Exception {
        assertThat(testee.isValidRecipient(mock(SMTPSession.class), new MailAddress(address)))
            .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "notFound@linagora.com",
        "bob@notFound.com",
        "not.found@linagora.com",
        "notfound@lists.james.org",
    })
    void shouldNotHandeRemoteResources(String address) throws Exception {
        assertThat(testee.isValidRecipient(mock(SMTPSession.class), new MailAddress(address)))
            .isFalse();
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer) {
        return ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of(ADMIN));
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer, Optional<Username> administrator) {
        PropertyListConfiguration configuration = baseConfiguration(ldapContainer);
        configuration.addProperty("[@userIdAttribute]", "mail");
        configuration.addProperty("supportsVirtualHosting", true);
        administrator.ifPresent(username -> configuration.addProperty("[@administratorId]", username.asString()));
        return configuration;
    }

    private static PropertyListConfiguration baseConfiguration(LdapGenericContainer ldapContainer) {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapHost());
        configuration.addProperty("[@principal]", "cn=admin,dc=james,dc=org");
        configuration.addProperty("[@credentials]", ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", "ou=people,dc=james,dc=org");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@connectionTimeout]", "2000");
        configuration.addProperty("[@readTimeout]", "2000");
        return configuration;
    }
}