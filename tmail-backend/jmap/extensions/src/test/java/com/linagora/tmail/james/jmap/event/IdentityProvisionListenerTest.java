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
 *******************************************************************/

package com.linagora.tmail.james.jmap.event;

import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.mail.internet.AddressException;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.jmap.api.identity.DefaultIdentitySupplier;
import org.apache.james.jmap.api.identity.IdentityCreationRequest;
import org.apache.james.jmap.api.identity.IdentityRepository;
import org.apache.james.jmap.api.model.Identity;
import org.apache.james.jmap.memory.identity.MemoryCustomIdentityDAO;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.AliasReverseResolverImpl;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.ldap.DockerLdapSingleton;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.user.ldap.ReadOnlyUsersLDAPRepository;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.team.TMailCanSendFrom;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepository;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class IdentityProvisionListenerTest {
    private static final LdapGenericContainer ldapContainer = DockerLdapSingleton.ldapContainer;
    private static final Username USER1 = Username.of("james-user@james.org");
    private static final Username USER2 = Username.of("james-user2@james.org");

    private InMemoryMailboxManager mailboxManager;
    private IdentityRepository identityRepository;
    private IdentityProvisionListener testee;
    private TeamMailboxRepository teamMailboxRepository;

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(mailboxManager.getMapperFactory(),
            mailboxManager.getMapperFactory(), mailboxManager.getEventBus());
        teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager, subscriptionManager, mailboxManager.getMapperFactory(), Set.of());
        LdapRepositoryConfiguration ldapRepositoryConfiguration = LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer));
        identityRepository = new IdentityRepository(new MemoryCustomIdentityDAO(), createDefaultIdentitySupplier(ldapRepositoryConfiguration));
        testee = new IdentityProvisionListener(ldapRepositoryConfiguration, identityRepository);
        mailboxManager.getEventBus().register(testee);
    }

    @Test
    void shouldProvisionDefaultIdentityWhenInboxCreated() {
        Mono.from(mailboxManager.createMailboxReactive(MailboxPath.inbox(USER1), mailboxManager.createSystemSession(USER1)))
            .subscribeOn(Schedulers.boundedElastic())
            .block();

        List<Identity> userSetIdentities = Flux.from(identityRepository.list(USER1))
            .filter(Identity::mayDelete)
            .collectList()
            .block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(userSetIdentities).hasSize(1);
            softly.assertThat(userSetIdentities.getFirst().name()).isEqualTo("firstname1 surname1");
            softly.assertThat(userSetIdentities.getFirst().email()).isEqualTo(USER1.asString());
            softly.assertThat(userSetIdentities.getFirst().sortOrder()).isEqualTo(0);
            softly.assertThat(userSetIdentities.getFirst().mayDelete()).isEqualTo(true);
        });
    }

    @Test
    void shouldNotProvisionDefaultIdentityWhenOtherMailboxThanInboxCreated() {
        Mono.from(mailboxManager.createMailboxReactive(MailboxPath.forUser(USER1, "Trash"), mailboxManager.createSystemSession(USER1)))
            .subscribeOn(Schedulers.boundedElastic())
            .block();

        List<Identity> userSetIdentities = Flux.from(identityRepository.list(USER1))
            .filter(Identity::mayDelete)
            .collectList()
            .block();

        assertThat(userSetIdentities).hasSize(0);
    }

    @Test
    void shouldNotProvisionDefaultIdentityWhenTeamMailboxCreated() throws AddressException {
        TeamMailbox teamMailbox = TeamMailbox.asTeamMailbox(new MailAddress("teamMailbox@james.org")).get();
        Mono.from(teamMailboxRepository.createTeamMailbox(teamMailbox)).block();

        List<Identity> userSetIdentities = Flux.from(identityRepository.list(teamMailbox.owner()))
            .filter(Identity::mayDelete)
            .collectList()
            .block();

        assertThat(userSetIdentities).hasSize(0);
    }

    @Test
    void shouldNotProvisionDefaultIdentityWhenUserAlreadyHasOne() throws AddressException {
        IdentityCreationRequest creationRequest = IdentityCreationRequest.fromJava(
            USER1.asMailAddress(),
            Optional.of("user defined identity"),
            Optional.empty(),
            Optional.empty(),
            Optional.of(1),
            Optional.of("textSignature 1"),
            Optional.of("htmlSignature 1"));
        Identity userDefinedIdentity = Mono.from(identityRepository.save(USER1, creationRequest)).block();

        // trigger INBOX creation
        Mono.from(mailboxManager.createMailboxReactive(MailboxPath.inbox(USER1), mailboxManager.createSystemSession(USER1)))
            .subscribeOn(Schedulers.boundedElastic())
            .block();

        List<Identity> userSetIdentities = Flux.from(identityRepository.list(USER1))
            .filter(Identity::mayDelete)
            .collectList()
            .block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(userSetIdentities).hasSize(1);
            softly.assertThat(userSetIdentities.getFirst()).isEqualTo(userDefinedIdentity);
        });
    }

    @Test
    void shouldNotImpactOtherUserIdentity() {
        // trigger INBOX creation for USER1
        Mono.from(mailboxManager.createMailboxReactive(MailboxPath.inbox(USER1), mailboxManager.createSystemSession(USER1)))
            .subscribeOn(Schedulers.boundedElastic())
            .block();

        // should not provision USER2 identity
        List<Identity> user2DefinedIdentities = Flux.from(identityRepository.list(USER2))
            .filter(Identity::mayDelete)
            .collectList()
            .block();

        assertThat(user2DefinedIdentities).isEmpty();
    }

    private HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer) {
        return ldapRepositoryConfigurationWithVirtualHosting(ldapContainer, Optional.of(ADMIN));
    }

    private HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfigurationWithVirtualHosting(LdapGenericContainer ldapContainer, Optional<Username> administrator) {
        PropertyListConfiguration configuration = baseConfiguration(ldapContainer);
        configuration.addProperty("[@userIdAttribute]", "mail");
        configuration.addProperty("supportsVirtualHosting", true);
        administrator.ifPresent(username -> configuration.addProperty("[@administratorId]", username.asString()));
        return configuration;
    }

    private PropertyListConfiguration baseConfiguration(LdapGenericContainer ldapContainer) {
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

    private DefaultIdentitySupplier createDefaultIdentitySupplier(LdapRepositoryConfiguration ldapRepositoryConfiguration) throws Exception {
        MemoryDomainList domainList = new MemoryDomainList(mock(DNSService.class));
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(Domain.of("james.org"));
        ReadOnlyUsersLDAPRepository ldapUserRepository = new ReadOnlyUsersLDAPRepository(domainList, new NoopGaugeRegistry(), ldapRepositoryConfiguration);
        ldapUserRepository.init();
        MemoryRecipientRewriteTable rrt = new MemoryRecipientRewriteTable();
        rrt.setUsersRepository(ldapUserRepository);
        rrt.setDomainList(domainList);
        rrt.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
        CanSendFrom canSendFrom = new TMailCanSendFrom(new AliasReverseResolverImpl(rrt), teamMailboxRepository);

        return new DefaultIdentitySupplier(canSendFrom, ldapUserRepository);
    }
}
