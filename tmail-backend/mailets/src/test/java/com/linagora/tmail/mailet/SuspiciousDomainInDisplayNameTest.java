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
import static org.mockito.Mockito.when;

import java.util.Collection;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuspiciousDomainInDisplayNameTest {

    private static final MailAddress RECIPIENT = newAddress("recipient@james.org");
    private static final MailAddress SENDER = newAddress("attacker@evil.org");
    private static final Domain LOCAL_DOMAIN = Domain.of("linagora.com");
    private static final Domain OTHER_DOMAIN = Domain.of("gmail.com");

    private SuspiciousDomainInDisplayName testee;

    @BeforeEach
    void setUp() throws Exception {
        MailetContext mailetContext = mock(MailetContext.class);
        when(mailetContext.isLocalServer(LOCAL_DOMAIN)).thenReturn(true);
        when(mailetContext.isLocalServer(OTHER_DOMAIN)).thenReturn(false);

        testee = new SuspiciousDomainInDisplayName();
        testee.init(FakeMatcherConfig.builder()
            .matcherName("SuspiciousDomainInDisplayName")
            .mailetContext(mailetContext)
            .build());
    }

    // --- match cases ---

    @Test
    void shouldMatchWhenDisplayNameContainsLocalDomain() throws Exception {
        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("John from linagora.com", SENDER));

        assertThat(matched).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenDisplayNameContainsEmailWithLocalDomain() throws Exception {
        // attacker puts a local-domain email address in his display name
        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("john@linagora.com", SENDER));

        assertThat(matched).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenLocalDomainIsInParentheses() throws Exception {
        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("John (linagora.com)", SENDER));

        assertThat(matched).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenLocalDomainAppearsAmongOtherText() throws Exception {
        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("Support linagora.com team", SENDER));

        assertThat(matched).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenDisplayNameContainsBothLocalAndExternalDomain() throws Exception {
        // local domain anywhere in the display name is enough to flag
        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("John gmail.com linagora.com", SENDER));

        assertThat(matched).containsOnly(RECIPIENT);
    }

    @Test
    void shouldNotMatchWhenFromHeaderAddressDomainMatchesDomainInDisplayName() throws Exception {
        // From: "John from linagora.com" <john@linagora.com> — From address domain == display name domain
        MailAddress localSender = newAddress("john@linagora.com");

        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("John from linagora.com", localSender));

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldMatchWhenFromHeaderAddressDomainDiffersFromDisplayNameDomain() throws Exception {
        // From: "John from linagora.com" <attacker@evil.org> — From address domain != display name domain
        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("John from linagora.com", SENDER));

        assertThat(matched).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenEnvelopeDomainIsLocalButFromHeaderDomainIsNot() throws Exception {
        // MAIL FROM: attacker@linagora.com, From: "John from linagora.com" <attacker@evil.org>
        // Envelope domain matches, but From header address domain doesn't → suspicious
        MailAddress envelopeSender = newAddress("attacker@linagora.com");
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(new InternetAddress("attacker@evil.org", "John from linagora.com"))
            .build();
        FakeMail mail = FakeMail.builder()
            .name("test")
            .sender(envelopeSender)
            .recipient(RECIPIENT)
            .mimeMessage(message)
            .build();

        assertThat(testee.match(mail)).containsOnly(RECIPIENT);
    }

    @Test
    void shouldNotMatchWhenFromHeaderDomainIsLocalEvenIfEnvelopeDomainDiffers() throws Exception {
        // MAIL FROM: forwarder@other.com (forwarding), From: "John from linagora.com" <john@linagora.com>
        // From header address domain matches display name domain → not suspicious
        MailAddress envelopeSender = newAddress("forwarder@other.com");
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(new InternetAddress("john@linagora.com", "John from linagora.com"))
            .build();
        FakeMail mail = FakeMail.builder()
            .name("test")
            .sender(envelopeSender)
            .recipient(RECIPIENT)
            .mimeMessage(message)
            .build();

        assertThat(testee.match(mail)).isEmpty();
    }

    // --- no-match cases ---

    @Test
    void shouldNotMatchWhenDisplayNameContainsOnlyExternalDomain() throws Exception {
        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("John from gmail.com", SENDER));

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldNotMatchWhenDisplayNameContainsEmailWithExternalDomain() throws Exception {
        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("john@gmail.com", SENDER));

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldNotMatchWhenDisplayNameHasNoDomain() throws Exception {
        Collection<MailAddress> matched = testee.match(mailWithFromDisplayName("John Doe", SENDER));

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldNotMatchWhenNoDisplayName() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(new InternetAddress(SENDER.asString()))
            .build();
        FakeMail mail = FakeMail.builder()
            .name("test")
            .sender(SENDER)
            .recipient(RECIPIENT)
            .mimeMessage(message)
            .build();

        assertThat(testee.match(mail)).isEmpty();
    }

    @Test
    void shouldNotMatchWhenNoFromHeader() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder().build();
        FakeMail mail = FakeMail.builder()
            .name("test")
            .sender(SENDER)
            .recipient(RECIPIENT)
            .mimeMessage(message)
            .build();

        assertThat(testee.match(mail)).isEmpty();
    }

    // --- helpers ---

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

    private static MailAddress newAddress(String address) {
        try {
            return new MailAddress(address);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
