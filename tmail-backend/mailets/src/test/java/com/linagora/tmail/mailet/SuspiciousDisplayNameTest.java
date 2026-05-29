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

import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Optional;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.user.ldap.DockerLdapSingleton;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SuspiciousDisplayNameTest {
    static LdapGenericContainer ldapContainer = DockerLdapSingleton.ldapContainer;

    private static final String USER_BASE = "ou=people,dc=james,dc=org";
    private static final String EMPTY_BASE = "ou=empty,dc=james,dc=org";
    private static final MailAddress RECIPIENT = newAddress("recipient@james.org");
    private static final MailAddress SENDER = newAddress("attacker@external.org");

    @BeforeAll
    static void setUpAll() {
        ldapContainer.start();
    }

    @AfterAll
    static void afterAll() {
        ldapContainer.stop();
    }

    // --- match cases ---

    @Test
    void shouldMatchWhenDisplayNameMatchesLdapUser() throws Exception {
        SuspiciousDisplayName testee = buildTestee(USER_BASE);

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("John Doe", SENDER));

        assertThat(matched).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenDisplayNameHasLeadingStopWord() throws Exception {
        // "Mr" is filtered → remaining tokens [John, Doe] → match
        SuspiciousDisplayName testee = buildTestee(USER_BASE);

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("Mr John Doe", SENDER));

        assertThat(matched).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenDisplayNameHasParentheses() throws Exception {
        // "(John Doe)" → punctuation stripped → [John, Doe] → match
        SuspiciousDisplayName testee = buildTestee(USER_BASE);

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("(John Doe)", SENDER));

        assertThat(matched).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenDisplayNameHasComma() throws Exception {
        // "John, Doe" → comma stripped → [John, Doe] → match
        SuspiciousDisplayName testee = buildTestee(USER_BASE);

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("John, Doe", SENDER));

        assertThat(matched).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenDisplayNameHasDot() throws Exception {
        // "John. Doe." → dots stripped → [John, Doe] → match
        SuspiciousDisplayName testee = buildTestee(USER_BASE);

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("John. Doe.", SENDER));

        assertThat(matched).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenShortTokenFiltered() throws Exception {
        // "Jo Doe" → "Jo" < 3 chars filtered → tokens [Doe] → substring match on "John Doe"
        SuspiciousDisplayName testee = buildTestee(USER_BASE);

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("Jo Doe", SENDER));

        assertThat(matched).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWithCustomStopWords() throws Exception {
        // "Dr" added as stop word via condition → "Dr John Doe" → [John, Doe] → match
        SuspiciousDisplayName testee = buildTesteeWithCondition(USER_BASE + "?stopWords=Dr");

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("Dr John Doe", SENDER));

        assertThat(matched).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenConditionIsEmpty() throws Exception {
        // empty condition → falls back to configuration.getUserBase() = ou=people,dc=james,dc=org
        SuspiciousDisplayName testee = buildTesteeWithCondition("");

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("John Doe", SENDER));

        assertThat(matched).containsOnly(RECIPIENT);
    }

    @Test
    void shouldNotMatchWhenSenderIsTheLdapUser() throws Exception {
        // john-doe@james.org sends with his own display name "John Doe" → not suspicious
        SuspiciousDisplayName testee = buildTestee(USER_BASE);
        MailAddress johnDoe = newAddress("john-doe@james.org");

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("John Doe", johnDoe));

        assertThat(matched).isEmpty();
    }

    // --- no-match cases ---

    @Test
    void shouldNotMatchWhenOnlyFirstTokenMatchesAnEntry() throws Exception {
        // "Alice Doe" → (&(displayName=*Alice*)(displayName=*Doe*)) → "John Doe" lacks "Alice" → no match
        SuspiciousDisplayName testee = buildTestee(USER_BASE);

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("Alice Doe", SENDER));

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldNotMatchWhenOnlyLastTokenMatchesAnEntry() throws Exception {
        // "John Martin" → (&(displayName=*John*)(displayName=*Martin*)) → "John Doe" lacks "Martin" → no match
        SuspiciousDisplayName testee = buildTestee(USER_BASE);

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("John Martin", SENDER));

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldNotMatchWhenDisplayNameUnknownInLdap() throws Exception {
        SuspiciousDisplayName testee = buildTestee(USER_BASE);

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("Alice Martin", SENDER));

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldNotMatchWhenNoDisplayName() throws Exception {
        // From: <attacker@external.org> — no personal name
        SuspiciousDisplayName testee = buildTestee(USER_BASE);
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(new InternetAddress("attacker@external.org"))
            .build();
        FakeMail mail = FakeMail.builder()
            .name("test")
            .sender(SENDER)
            .recipient(RECIPIENT)
            .mimeMessage(message)
            .build();

        Collection<MailAddress> matched = testee.match(mail);

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldNotMatchWhenNoFromHeader() throws Exception {
        SuspiciousDisplayName testee = buildTestee(USER_BASE);
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder().build();
        FakeMail mail = FakeMail.builder()
            .name("test")
            .sender(SENDER)
            .recipient(RECIPIENT)
            .mimeMessage(message)
            .build();

        Collection<MailAddress> matched = testee.match(mail);

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldNotMatchWhenUserBasePointsToEmptyOU() throws Exception {
        SuspiciousDisplayName testee = buildTestee(EMPTY_BASE);

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("John Doe", SENDER));

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldNotMatchWhenAllTokensShorterThanThreeChars() throws Exception {
        // "Jo Do" → both < 3 chars → no tokens → no match
        SuspiciousDisplayName testee = buildTestee(USER_BASE);

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("Jo Do", SENDER));

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldNotMatchWhenOnlyStopWordsRemainAfterTokenizing() throws Exception {
        // "Mr Mme" → both are stop words → no tokens → no match
        SuspiciousDisplayName testee = buildTestee(USER_BASE);

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("Mr Mme", SENDER));

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldNotMatchWhenCustomStopWordsDoNotFilterDefaultOnes() throws Exception {
        // condition replaces stop words with only "Dr" → "Mme" is no longer a stop word
        // "Mme John Doe" → tokens [Mme, John, Doe] → (&(*Mme*)(*John*)(*Doe*)) → "John Doe" lacks "Mme" → no match
        SuspiciousDisplayName testee = buildTesteeWithCondition(USER_BASE + "?stopWords=Dr");

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("Mme John Doe", SENDER));

        assertThat(matched).isEmpty();
    }

    // --- helpers ---

    private SuspiciousDisplayName buildTestee(String userBase) throws Exception {
        return buildTesteeWithCondition(userBase);
    }

    private SuspiciousDisplayName buildTesteeWithCondition(String condition) throws Exception {
        SuspiciousDisplayName testee = new SuspiciousDisplayName(
            LdapRepositoryConfiguration.from(ldapRepositoryConfiguration(ldapContainer)));
        testee.init(FakeMatcherConfig.builder()
            .matcherName("SuspiciousDisplayName")
            .condition(condition)
            .build());
        return testee;
    }

    private FakeMail mailWithFromDisplayName(String displayName, MailAddress sender) throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(new InternetAddress(sender.asString(), displayName))
            .build();
        return FakeMail.builder()
            .name("test")
            .sender(sender)
            .recipient(RECIPIENT)
            .mimeMessage(message)
            .build();
    }

    static HierarchicalConfiguration<ImmutableNode> ldapRepositoryConfiguration(LdapGenericContainer ldapContainer) {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapContainer.getLdapHost());
        configuration.addProperty("[@principal]", "cn=admin,dc=james,dc=org");
        configuration.addProperty("[@credentials]", ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", USER_BASE);
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@userIdAttribute]", "mail");
        configuration.addProperty("supportsVirtualHosting", true);
        configuration.addProperty("[@administratorId]", ADMIN.asString());
        configuration.addProperty("[@connectionTimeout]", "2000");
        configuration.addProperty("[@readTimeout]", "2000");
        return configuration;
    }

    private static MailAddress newAddress(String address) {
        try {
            return new MailAddress(address);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
