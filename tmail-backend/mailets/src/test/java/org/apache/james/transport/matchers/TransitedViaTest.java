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

package org.apache.james.transport.matchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransitedViaTest {
    static final String RECIPIENT = "bob@twake.app";
    static final String RECEIVED_BY_TWAKE = "from mx.example.com (mx.example.com [10.0.0.1])\r\n" +
        "\tby smtp.twake.app (JAMES SMTP Server ) with ESMTP ID 1234\r\n" +
        "\tfor <bob@twake.app>; Wed, 7 Oct 2026 10:00:00 +0000 (UTC)";
    static final String RECEIVED_BY_EXAMPLE = "from client.example.com ([10.0.0.2])\r\n" +
        "\tby mx.example.com with ESMTP id 5678; Wed, 7 Oct 2026 09:59:00 +0000";

    TransitedVia testee;

    @BeforeEach
    void setUp() {
        testee = new TransitedVia();
    }

    static Mail mailWithReceivedHeaders(String... receivedHeaders) throws MessagingException {
        MimeMessageBuilder message = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("subject")
            .setText("body");
        Arrays.stream(receivedHeaders)
            .forEach(receivedHeader -> message.addHeader("Received", receivedHeader));
        return FakeMail.builder()
            .name("mail")
            .recipient(RECIPIENT)
            .mimeMessage(message)
            .build();
    }

    void init(String condition) throws MessagingException {
        testee.init(FakeMatcherConfig.builder()
            .matcherName("TransitedVia")
            .condition(condition)
            .build());
    }

    @Test
    void initShouldThrowWhenNoCondition() {
        assertThatThrownBy(() -> testee.init(FakeMatcherConfig.builder()
                .matcherName("TransitedVia")
                .build()))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void initShouldThrowWhenBlankCondition() {
        assertThatThrownBy(() -> init("  "))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void shouldNotMatchWhenNoReceivedHeader() throws Exception {
        init("smtp.twake.app");

        assertThat(testee.match(mailWithReceivedHeaders())).isEmpty();
    }

    @Test
    void shouldNotMatchWhenReceivedHeadersDoNotMentionTheHost() throws Exception {
        init("smtp.twake.app");

        assertThat(testee.match(mailWithReceivedHeaders(RECEIVED_BY_EXAMPLE))).isEmpty();
    }

    @Test
    void shouldMatchWhenAReceivedHeaderMentionsTheHost() throws Exception {
        init("smtp.twake.app");

        assertThat(testee.match(mailWithReceivedHeaders(RECEIVED_BY_EXAMPLE, RECEIVED_BY_TWAKE)))
            .containsOnly(new MailAddress(RECIPIENT));
    }

    @Test
    void shouldBeCaseInsensitive() throws Exception {
        init("SMTP.Twake.App");

        assertThat(testee.match(mailWithReceivedHeaders(RECEIVED_BY_TWAKE)))
            .containsOnly(new MailAddress(RECIPIENT));
    }

    @Test
    void shouldMatchIp() throws Exception {
        init("10.0.0.1");

        assertThat(testee.match(mailWithReceivedHeaders(RECEIVED_BY_TWAKE)))
            .containsOnly(new MailAddress(RECIPIENT));
    }

    @Test
    void shouldNotMatchIpPrefix() throws Exception {
        init("10.0.0.1");

        assertThat(testee.match(mailWithReceivedHeaders("from client.example.com ([10.0.0.12]) by mx.example.com")))
            .isEmpty();
    }

    @Test
    void shouldNotMatchHostNamePrefix() throws Exception {
        init("smtp.twake.app");

        assertThat(testee.match(mailWithReceivedHeaders("by smtp.twake.application with ESMTP")))
            .isEmpty();
    }

    @Test
    void shouldNotMatchSubDomain() throws Exception {
        init("smtp.twake.app");

        assertThat(testee.match(mailWithReceivedHeaders("by relay.smtp.twake.app with ESMTP")))
            .isEmpty();
    }

    @Test
    void shouldMatchFullyQualifiedHostNameWithTrailingDot() throws Exception {
        init("smtp.twake.app");

        assertThat(testee.match(mailWithReceivedHeaders("by smtp.twake.app. with ESMTP")))
            .containsOnly(new MailAddress(RECIPIENT));
    }

    @Test
    void shouldNotInterpretConditionAsARegex() throws Exception {
        init("smtp.twake.app");

        assertThat(testee.match(mailWithReceivedHeaders("by smtpXtwakeXapp with ESMTP")))
            .isEmpty();
    }
}
