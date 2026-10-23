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
package com.linagora.tmail.agent;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import io.restassured.path.json.JsonPath;
import jakarta.mail.Flags;
import org.apache.james.JamesServerExtension;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GetEmailDetailsToolTest extends AiToolsTestBase {

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = server();

    private static final Instant RECEIVED_AT = Instant.parse("2026-10-14T08:30:00Z");

    @Test
    void shouldReturnTheDetailsOfTheEmail() throws Exception {
        MessageId id = append(INBOX,
            email("Alice <alice@domain.tld>", "Meeting", "Shall we meet tomorrow?")
                .setCc(new Mailbox("bob", "domain.tld")),
            RECEIVED_AT);

        String content = callTool(GET_EMAIL_DETAILS, "{\"id\": \"" + id.serialize() + "\"}");

        assertThatJson(content).isEqualTo("""
            {
              "id": "%s",
              "from": ["Alice <alice@domain.tld>"],
              "to": ["paying@domain.tld"],
              "cc": ["bob@domain.tld"],
              "subject": "Meeting",
              "receivedAt": "2026-10-14T08:30:00Z",
              "mailboxIds": ["%s"],
              "unread": true,
              "attachments": [],
              "body": "Shall we meet tomorrow?",
              "truncated": false
            }""".formatted(id.serialize(), inboxId.serialize()));
    }

    @Test
    void shouldTellAReadEmailIsNotUnread() throws Exception {
        MessageId id = append(INBOX, email("alice@domain.tld", "Read", "Body"),
            RECEIVED_AT, new Flags(Flags.Flag.SEEN));

        String content = callTool(GET_EMAIL_DETAILS, "{\"id\": \"" + id.serialize() + "\"}");

        assertThatJson(content).node("unread").isEqualTo(false);
    }

    @Test
    void shouldListTheNamesOfTheAttachments() throws Exception {
        Message.Builder message = Message.Builder.of()
            .setFrom("alice@domain.tld")
            .setTo("paying@domain.tld")
            .setSubject("Report")
            .setBody(MultipartBuilder.create("mixed")
                .addBodyPart(BodyPartBuilder.create().setBody("See the report", StandardCharsets.UTF_8))
                .addBodyPart(BodyPartBuilder.create()
                    .setBody("%PDF-1.4".getBytes(StandardCharsets.UTF_8), "application/pdf")
                    .setContentDisposition("attachment", "report.pdf"))
                .build());
        MessageId id = append(INBOX, message, RECEIVED_AT);

        String content = callTool(GET_EMAIL_DETAILS, "{\"id\": \"" + id.serialize() + "\"}");

        assertThatJson(content).node("attachments").isEqualTo("[\"report.pdf\"]");
        assertThatJson(content).node("body").isEqualTo("See the report");
    }

    @Test
    void shouldReadTheTextOfAnHtmlOnlyEmail() throws Exception {
        Message.Builder message = Message.Builder.of()
            .setFrom("alice@domain.tld")
            .setTo("paying@domain.tld")
            .setSubject("Html")
            .setBody("<p>Hello <b>Paying</b></p>", "html", StandardCharsets.UTF_8);
        MessageId id = append(INBOX, message, RECEIVED_AT);

        String content = callTool(GET_EMAIL_DETAILS, "{\"id\": \"" + id.serialize() + "\"}");

        assertThatJson(content).node("body").asString().contains("Hello").contains("Paying").doesNotContain("<b>");
    }

    @Test
    void shouldTruncateALongBody() throws Exception {
        MessageId id = append(INBOX, email("alice@domain.tld", "Long", "a".repeat(6000)),
            RECEIVED_AT);

        String content = callTool(GET_EMAIL_DETAILS, "{\"id\": \"" + id.serialize() + "\"}");

        assertThatJson(content).node("truncated").isEqualTo(true);
        assertThatJson(content).node("body").isEqualTo("a".repeat(5000) + "…");
    }

    @Test
    void shouldGiveAllTheMailboxesOfTheEmail() throws Exception {
        ComposedMessageId composed = appendComposed(INBOX,
            email("alice@domain.tld", "Copied", "Body"), RECEIVED_AT, new Flags());
        mailboxProbe.copy(PAYING_USER, INBOX, ARCHIVE, composed.getUid());
        MessageId id = composed.getMessageId();

        String content = callTool(GET_EMAIL_DETAILS, "{\"id\": \"" + id.serialize() + "\"}");

        assertThat(JsonPath.from(content).getList("mailboxIds", String.class))
            .containsExactlyInAnyOrder(inboxId.serialize(), archiveId.serialize());
    }

    @Test
    void shouldNotFindTheEmailOfAnotherUser() throws Exception {
        MessageId id = append(MailboxPath.inbox(NON_PAYING_USER),
            email("alice@domain.tld", "Private", "Secret").setTo("nonpaying@domain.tld"), RECEIVED_AT);

        postTool(GET_EMAIL_DETAILS, "{\"id\": \"" + id.serialize() + "\"}")
            .statusCode(SC_NOT_FOUND)
            .body("detail", equalTo("Email " + id.serialize() + " not found"));
    }

    @Test
    void shouldNotFindADeletedEmail() throws Exception {
        ComposedMessageId composed = appendComposed(INBOX,
            email("alice@domain.tld", "Gone", "Body"), RECEIVED_AT, new Flags());
        mailboxProbe.deleteMessage(List.of(composed.getUid()), INBOX, PAYING_USER);
        MessageId id = composed.getMessageId();

        postTool(GET_EMAIL_DETAILS, "{\"id\": \"" + id.serialize() + "\"}")
            .statusCode(SC_NOT_FOUND);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCalls")
    void shouldRejectAnInvalidCall(String description, String arguments, int status, String detail) {
        postTool(GET_EMAIL_DETAILS, arguments)
            .statusCode(status)
            .body("detail", equalTo(detail));
    }

    static Stream<Arguments> invalidCalls() {
        return Stream.of(
            Arguments.of("shouldRejectACallWithoutId", "{}", SC_BAD_REQUEST, "Missing 'id'"),
            Arguments.of("shouldRejectAMalformedId", "{\"id\": \"not-an-id\"}", SC_BAD_REQUEST, "Invalid email id: not-an-id"));
    }
}
