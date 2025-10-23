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

package com.linagora.tmail.james.jmap.contact;


import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;

import jakarta.mail.internet.AddressException;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.user.ldap.DockerLdapSingleton;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepository;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;
import com.unboundid.ldap.sdk.LDAPConnectionPool;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DomainContactProvisionerListenerIntegrationTest {
    private static final LdapGenericContainer ldapContainer = DockerLdapSingleton.ldapContainer;

    private static final Username USER1 = Username.of("james-user@james.org");
    private static final Domain DOMAIN = Domain.of("james.org");
    private static final Username ALICE = Username.of("alice@ignored.org");
    private static final Username CEDRIC = Username.of("cedric@blacklist.org");
    private static final String IGNORED_DOMAINS = "ignored.org,blacklist.org";
    private static final MailboxPath INBOX_PATH = MailboxPath.inbox(USER1);

    private EmailAddressContactSearchEngine contactSearchEngine;
    private InMemoryMailboxManager mailboxManager;
    private TeamMailboxRepository teamMailboxRepository;

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    private void setup(HierarchicalConfiguration<ImmutableNode> configuration) throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(mailboxManager.getMapperFactory(),
            mailboxManager.getMapperFactory(), mailboxManager.getEventBus());
        teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager, subscriptionManager, mailboxManager.getMapperFactory(), Set.of());
        contactSearchEngine = new InMemoryEmailAddressContactSearchEngine();
        LdapRepositoryConfiguration ldapRepositoryConfiguration = LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer));
        LDAPConnectionPool ldapConnectionPool = new LDAPConnectionFactory(ldapRepositoryConfiguration).getLdapConnectionPool();
        DomainContactProvisionerListener testee = new DomainContactProvisionerListener(contactSearchEngine, ldapConnectionPool, ldapRepositoryConfiguration, configuration);
        mailboxManager.getEventBus().register(testee);
    }

    @Nested
    class EmptyConfiguration {
        @BeforeEach
        void setUp() throws Exception {
            HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();

            setup(configuration);
        }

        @Test
        void shouldIndexDomainContactWhenListenerHasNoConfiguration() throws MailboxException, AddressException {
            mailboxManager.createMailbox(INBOX_PATH, mailboxManager.createSystemSession(USER1));

            assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
                .containsExactlyInAnyOrder(new ContactFields(new MailAddress(USER1.asString()), "firstname1", "surname1"));
        }
    }

    @Nested
    class WrongLdapConfiguration {
        @BeforeEach
        void setUp() throws Exception {
            HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
            configuration.addProperty("firstnameAttribute", "firstname");
            configuration.addProperty("surnameAttribute", "surname");

            setup(configuration);
        }

        @Test
        void shouldIndexDomainContactWithEmptyNameWhenListenerHasWrongLdapConfiguration() throws MailboxException, AddressException {
            mailboxManager.createMailbox(INBOX_PATH, mailboxManager.createSystemSession(USER1));

            assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
                .containsExactlyInAnyOrder(new ContactFields(new MailAddress(USER1.asString()), "", ""));
        }
    }

    @Nested
    class NormalIndexation {
        @BeforeEach
        void setUp() throws Exception {
            HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
            configuration.addProperty("ignoredDomains", IGNORED_DOMAINS);
            configuration.addProperty("firstnameAttribute", "givenName");
            configuration.addProperty("surnameAttribute", "sn");

            setup(configuration);
        }

        @Test
        void shouldIndexDomainContactWhenInboxAdded() throws AddressException, MailboxException {
            mailboxManager.createMailbox(INBOX_PATH, mailboxManager.createSystemSession(USER1));

            assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
                .containsExactlyInAnyOrder(new ContactFields(new MailAddress(USER1.asString()), "firstname1", "surname1"));
        }

        @Test
        void shouldNotIndexDomainContactWhenMailboxAddedNotInbox() throws MailboxException {
            mailboxManager.createMailbox(MailboxPath.forUser(USER1, "otherMailbox"), mailboxManager.createSystemSession(USER1));

            assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
                .isEmpty();
        }

        @Test
        void shouldNotIndexDomainContactWhenDomainIsBlacklisted() throws MailboxException {
            mailboxManager.createMailbox(MailboxPath.inbox(ALICE), mailboxManager.createSystemSession(ALICE));

            assertThat(Flux.from(contactSearchEngine.list(Domain.of("ignored.org")))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
                .isEmpty();
        }

        @Test
        void indexShouldBeIdempotent() throws AddressException, MailboxException {
            Mono.from(contactSearchEngine.index(DOMAIN, new ContactFields(new MailAddress(USER1.asString()), "firstname1", "surname1"))).block();
            mailboxManager.createMailbox(INBOX_PATH, mailboxManager.createSystemSession(USER1));

            assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
                .containsExactlyInAnyOrder(new ContactFields(new MailAddress(USER1.asString()), "firstname1", "surname1"));
        }

        @Test
        void shouldIndexDomainContactWithDefaultValuesWhenNoLdapEntryFound() throws AddressException, MailboxException {
            Username bob = Username.of("bob@james.org");
            MailboxPath mailboxPath  = MailboxPath.inbox(bob);
            mailboxManager.createMailbox(mailboxPath, mailboxManager.createSystemSession(bob));

            assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
                .containsExactlyInAnyOrder(new ContactFields(new MailAddress(bob.asString()), "", ""));
        }

        @Test
        void shouldNotProvisionDomainContactWhenTeamMailboxCreated() throws AddressException {
            TeamMailbox teamMailbox = TeamMailbox.asTeamMailbox(new MailAddress("teamMailbox@james.org")).get();
            Mono.from(teamMailboxRepository.createTeamMailbox(teamMailbox)).block();

            assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
                .isEmpty();
        }

        @Test
        void shouldRemoveDomainContactWhenInboxDeleted() throws MailboxException {
            MailboxSession session = mailboxManager.createSystemSession(USER1);
            mailboxManager.createMailbox(INBOX_PATH, session);
            mailboxManager.deleteMailbox(INBOX_PATH, session);

            assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
                .isEmpty();
        }

        @Test
        void shouldNotRemoveDomainContactWhenMailboxDeletedNotInbox() throws AddressException, MailboxException {
            MailboxSession session = mailboxManager.createSystemSession(USER1);
            MailboxPath otherPath = MailboxPath.forUser(USER1, "otherMailbox");
            mailboxManager.createMailbox(INBOX_PATH, session);
            mailboxManager.createMailbox(otherPath, session);
            mailboxManager.deleteMailbox(otherPath, session);

            assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
                .containsExactlyInAnyOrder(new ContactFields(new MailAddress(USER1.asString()), "firstname1", "surname1"));
        }


        @Test
        void shouldNotRemoveDomainContactWhenDomainIsBlacklisted() throws AddressException, MailboxException {
            Domain blacklistedDomain = Domain.of("blacklist.org");
            ContactFields contact = new ContactFields(new MailAddress(CEDRIC.asString()), "cedric", "blacklist");
            Mono.from(contactSearchEngine.index(blacklistedDomain, contact)).block();

            MailboxSession session = mailboxManager.createSystemSession(CEDRIC);
            MailboxPath cedricInboxPath = MailboxPath.inbox(CEDRIC);
            mailboxManager.createMailbox(cedricInboxPath, session);
            mailboxManager.deleteMailbox(cedricInboxPath, session);

            assertThat(Flux.from(contactSearchEngine.list(blacklistedDomain))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
                .containsExactlyInAnyOrder(contact);
        }
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
}
