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

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlignFromHeaderWithMailFromTest {
    private static final String ORIGINAL_FROM_HEADER = "Original-From";
    private static final String FROM_HEADER = "From";

    private Mailet mailet;

    @BeforeEach
    void setUp() {
        mailet = new AlignFromHeaderWithMailFrom();
    }

    @Test
    void getMailetInfoShouldReturnExpectedValue() {
        assertThat(mailet.getMailetInfo()).isEqualTo("AlignFromHeaderWithMailFrom Mailet");
    }

    @Test
    void shouldAlignFromHeaderWithMailFrom() throws Exception {
        mailet.init(FakeMailetConfig.builder().build());

        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(new InternetAddress("bob@external.com", "Bob External"))
            .build();

        Mail mail = FakeMail.builder()
            .name("mail")
            .sender("alice@us.com")
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(FROM_HEADER)).containsExactly("\"Bob External (bob@external.com)\" <alice@us.com>");
        assertThat(mail.getMessage().getHeader(ORIGINAL_FROM_HEADER)).containsExactly("bob@external.com");
    }

    @Test
    void shouldAlignFromHeaderWithoutPersonalName() throws Exception {
        mailet.init(FakeMailetConfig.builder().build());

        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(new InternetAddress("bob@external.com"))
            .build();

        Mail mail = FakeMail.builder()
            .name("mail")
            .sender("alice@us.com")
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(FROM_HEADER)).containsExactly("\"(bob@external.com)\" <alice@us.com>");
        assertThat(mail.getMessage().getHeader(ORIGINAL_FROM_HEADER)).containsExactly("bob@external.com");
    }

    @Test
    void shouldSkipWhenFromHeaderAlreadyAligned() throws Exception {
        mailet.init(FakeMailetConfig.builder().build());

        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(new InternetAddress("alice@us.com", "Alice"))
            .build();

        Mail mail = FakeMail.builder()
            .name("mail")
            .sender("alice@us.com")
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(FROM_HEADER)).containsExactly("Alice <alice@us.com>");
        assertThat(mail.getMessage().getHeader(ORIGINAL_FROM_HEADER)).isNull();
    }

    @Test
    void shouldSkipWhenNoMailFromAddress() throws Exception {
        mailet.init(FakeMailetConfig.builder().build());

        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(new InternetAddress("bob@external.com", "Bob External"))
            .build();

        Mail mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(FROM_HEADER)).containsExactly("Bob External <bob@external.com>");
        assertThat(mail.getMessage().getHeader(ORIGINAL_FROM_HEADER)).isNull();
    }

    @Test
    void shouldSkipWhenNoFromHeader() throws Exception {
        mailet.init(FakeMailetConfig.builder().build());

        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .build();

        Mail mail = FakeMail.builder()
            .name("mail")
            .sender("alice@us.com")
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(FROM_HEADER)).isNull();
        assertThat(mail.getMessage().getHeader(ORIGINAL_FROM_HEADER)).isNull();
    }

    @Test
    void shouldHandleComplexDisplayNames() throws Exception {
        mailet.init(FakeMailetConfig.builder().build());

        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(new InternetAddress("bob@external.com", "Bob \"The Builder\" External"))
            .build();

        Mail mail = FakeMail.builder()
            .name("mail")
            .sender("alice@us.com")
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(FROM_HEADER)).containsExactly("\"Bob \\\"The Builder\\\" External (bob@external.com)\" <alice@us.com>");
        assertThat(mail.getMessage().getHeader(ORIGINAL_FROM_HEADER)).containsExactly("bob@external.com");
    }

    @Test
    void shouldHandleCaseInsensitiveComparison() throws Exception {
        mailet.init(FakeMailetConfig.builder().build());

        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom(new InternetAddress("ALICE@US.COM", "Alice"))
            .build();

        Mail mail = FakeMail.builder()
            .name("mail")
            .sender("alice@us.com")
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        assertThat(mail.getMessage().getHeader(FROM_HEADER)).containsExactly("Alice <ALICE@US.COM>");
        assertThat(mail.getMessage().getHeader(ORIGINAL_FROM_HEADER)).isNull();
    }
}
