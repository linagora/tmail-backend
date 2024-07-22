package com.linagora.tmail.mailet;

import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.user.ldap.DockerLdapSingleton;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IsALDAPMailingListTest {
    static LdapGenericContainer ldapContainer = DockerLdapSingleton.ldapContainer;

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    @Test
    void shouldMatchGroups() throws Exception {
        IsALDAPMailingList testee = new IsALDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        testee.init(FakeMatcherConfig.builder()
            .matcherName("IsALDAPMailingList")
            .condition("ou=lists,dc=james,dc=org#groupofnames#description")
            .build());

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@james.org")
            .recipient("mygroup@lists.james.org")
            .build();
        testee.match(mail);

        assertThat(testee.match(mail))
            .containsOnly(new MailAddress("mygroup@lists.james.org"));
    }

    @Test
    void shouldNotMatchNonExistingGroups() throws Exception {
        IsALDAPMailingList testee = new IsALDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        testee.init(FakeMatcherConfig.builder()
            .matcherName("IsALDAPMailingList")
            .condition("ou=lists,dc=james,dc=org#groupofnames#description")
            .build());

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@james.org")
            .recipient("not-found@lists.james.org")
            .build();
        testee.match(mail);

        assertThat(testee.match(mail))
            .isEmpty();
    }

    @Test
    void shouldNotMatchUsers() throws Exception {
        IsALDAPMailingList testee = new IsALDAPMailingList(LdapRepositoryConfiguration.from(ldapRepositoryConfigurationWithVirtualHosting(ldapContainer)));
        testee.init(FakeMatcherConfig.builder()
            .matcherName("IsALDAPMailingList")
            .condition("ou=lists,dc=james,dc=org#groupofnames#description")
            .build());

        FakeMail mail = FakeMail.builder()
            .name("test-mail")
            .state(FakeMail.DEFAULT)
            .sender("bob@james.org")
            .recipient("james-user@james.org")
            .build();
        testee.match(mail);

        assertThat(testee.match(mail))
            .isEmpty();
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