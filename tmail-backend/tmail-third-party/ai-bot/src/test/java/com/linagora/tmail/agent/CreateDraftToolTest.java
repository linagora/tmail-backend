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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.path.json.JsonPath;
import jakarta.mail.Flags;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.MessageIdProbe;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.util.mime.MessageContentExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CreateDraftToolTest extends AiToolsTestBase {

    private static final String TOOL = "create_draft";

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = server();

    private MessageIdProbe messageIdProbe;

    @BeforeEach
    void setUpProbe(GuiceJamesServer jamesServer) {
        messageIdProbe = jamesServer.getProbe(MessageIdProbe.class);
    }

    @Test
    void shouldCreateTheDraftInTheDraftsMailbox() {
        String content = callTool("create_draft", """
            {
              "to": ["user2@domain.tld"],
              "cc": ["bob@domain.tld"],
              "subject": "Réunion",
              "body": "Bonjour, on se voit demain ?"
            }""");

        String draftId = JsonPath.from(content).getString("id");
        String draftsId = mailboxProbe.getMailboxId(DRAFTS.getNamespace(), PAYING_USER.asString(), DRAFTS.getName()).serialize();
        assertThatJson(content).isEqualTo("""
            {
              "id": "%s",
              "mailboxId": "%s",
              "subject": "Réunion"
            }""".formatted(draftId, draftsId));
        assertThatJson(callTool("get_email_details", "{\"id\": \"" + draftId + "\"}")).isEqualTo("""
            {
              "id": "%s",
              "from": ["paying@domain.tld"],
              "to": ["user2@domain.tld"],
              "cc": ["bob@domain.tld"],
              "subject": "Réunion",
              "receivedAt": "${json-unit.any-string}",
              "mailboxIds": ["%s"],
              "unread": false,
              "attachments": [],
              "body": "Bonjour, on se voit demain ?",
              "truncated": false
            }""".formatted(draftId, draftsId));
    }

    @Test
    void shouldFlagTheEmailAsADraft() throws Exception {
        String content = callTool("create_draft", "{\"to\": [\"user2@domain.tld\"], \"body\": \"Hello\"}");

        MessageResult draft = stored(JsonPath.from(content).getString("id"));
        assertThat(draft.getFlags().contains(Flags.Flag.DRAFT)).isTrue();
    }

    @Test
    void shouldReadARecipientSentAsAString() {
        String content = callTool("create_draft", "{\"to\": \"user2@domain.tld\", \"body\": \"Hello\"}");

        assertThatJson(callTool("get_email_details", "{\"id\": \"" + JsonPath.from(content).getString("id") + "\"}"))
            .node("to").isEqualTo("[\"user2@domain.tld\"]");
    }

    @Test
    void shouldCreateADraftWithoutRecipientNorSubject() {
        String content = callTool("create_draft", "{\"body\": \"Just a note\"}");

        assertThatJson(content).node("subject").isEqualTo("");
        assertThatJson(callTool("get_email_details", "{\"id\": \"" + JsonPath.from(content).getString("id") + "\"}"))
            .node("to").isArray().isEmpty();
    }

    @Test
    void shouldCreateTheDraftsMailboxWhenMissing() {
        assertThat(mailboxProbe.listUserMailboxes(PAYING_USER.asString())).doesNotContain(DRAFTS.getName());

        callTool("create_draft", "{\"body\": \"Hello\"}");

        assertThat(mailboxProbe.listUserMailboxes(PAYING_USER.asString())).contains(DRAFTS.getName());
    }

    @Test
    void shouldDraftAReplyToAnEmail() throws Exception {
        MessageId original = append(INBOX,
            email("alice@domain.tld", "paying@domain.tld", "Meeting", "Shall we meet?")
                .setMessageId("<original@domain.tld>"),
            Instant.parse("2026-10-14T09:00:00Z"));

        String content = callTool("create_draft", """
            {
              "to": ["alice@domain.tld"],
              "body": "Yes, tomorrow at 10",
              "inReplyTo": "%s"
            }""".formatted(original.serialize()));

        assertThatJson(content).node("subject").isEqualTo("Re: Meeting");
        Message draft = parse(stored(JsonPath.from(content).getString("id")));
        assertThat(draft.getHeader().getField("In-Reply-To").getBody()).isEqualTo("<original@domain.tld>");
        assertThat(draft.getHeader().getField("References").getBody()).isEqualTo("<original@domain.tld>");
    }

    @Test
    void shouldKeepTheReferencesOfTheThread() throws Exception {
        MessageId original = append(INBOX,
            email("alice@domain.tld", "paying@domain.tld", "Re: Meeting", "Shall we meet?")
                .setMessageId("<second@domain.tld>")
                .setField(new RawField("References", "<first@domain.tld>")),
            Instant.parse("2026-10-14T09:00:00Z"));

        String content = callTool("create_draft",
            "{\"body\": \"Yes\", \"inReplyTo\": \"" + original.serialize() + "\"}");

        assertThatJson(content).node("subject").isEqualTo("Re: Meeting");
        Message draft = parse(stored(JsonPath.from(content).getString("id")));
        assertThat(draft.getHeader().getField("References").getBody())
            .isEqualTo("<first@domain.tld> <second@domain.tld>");
    }

    @Test
    void shouldKeepTheSubjectGivenForAReply() throws Exception {
        MessageId original = append(INBOX, email("alice@domain.tld", "paying@domain.tld", "Meeting", "Shall we meet?"),
            Instant.parse("2026-10-14T09:00:00Z"));

        String content = callTool("create_draft",
            "{\"body\": \"Yes\", \"subject\": \"About tomorrow\", \"inReplyTo\": \"" + original.serialize() + "\"}");

        assertThatJson(content).node("subject").isEqualTo("About tomorrow");
    }

    @Test
    void shouldNotReplyToTheEmailOfAnotherUser() throws Exception {
        MessageId othersEmail = append(MailboxPath.inbox(NON_PAYING_USER),
            email("alice@domain.tld", "nonpaying@domain.tld", "Private", "Secret"), Instant.parse("2026-10-14T09:00:00Z"));

        postTool("create_draft", "{\"body\": \"Yes\", \"inReplyTo\": \"" + othersEmail.serialize() + "\"}")
            .statusCode(SC_NOT_FOUND);
    }

    @Test
    void shouldStoreTheMarkdownAsTextAndItsRenderingAsHtml() throws Exception {
        String markdown = "Hello **Alice**, *see* [the doc](https://example.com):\n\n- one\n- two";

        Message draft = draft(callTool("create_draft", bodyArgument(markdown)));

        assertThat(draft.getMimeType()).isEqualTo("multipart/alternative");
        MessageContentExtractor.MessageContent content = new MessageContentExtractor().extract(draft);
        assertThat(lf(content.getTextBody().orElseThrow())).isEqualTo(markdown);
        assertThat(lf(content.getHtmlBody().orElseThrow()))
            .contains("<strong>Alice</strong>")
            .contains("<em>see</em>")
            .contains("<a rel=\"nofollow\" href=\"https://example.com\">the doc</a>")
            .contains("<ul>\n<li>one</li>\n<li>two</li>\n</ul>");
    }

    @Test
    void shouldKeepTheLineBreaksOfTheBody() throws Exception {
        Message draft = draft(callTool("create_draft", bodyArgument("Bonjour,\nÀ demain")));

        assertThat(html(draft))
            .contains("<p>Bonjour,<br />\nÀ demain</p>");
    }

    @Test
    void shouldEscapeTheHtmlWrittenInTheBody() throws Exception {
        Message draft = draft(callTool("create_draft", bodyArgument("<script>alert(1)</script> <b>bold</b>")));

        assertThat(html(draft))
            .doesNotContain("<script>")
            .doesNotContain("<b>")
            .contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void shouldDropJavascriptLinks() throws Exception {
        Message draft = draft(callTool("create_draft", bodyArgument("[click](javascript:alert(1))")));

        assertThat(html(draft))
            .doesNotContain("javascript:")
            .contains(">click</a>");
    }

    /** The draft as James stored it, found in the Drafts mailbox */
    private MessageResult stored(String id) throws Exception {
        MailboxId draftsId = mailboxProbe.getMailboxId(DRAFTS.getNamespace(), PAYING_USER.asString(), DRAFTS.getName());
        MessageId messageId = mailboxProbe.searchMessage(
                MultimailboxesSearchQuery.from(SearchQuery.matchAll()).inMailboxes(draftsId).build(), PAYING_USER.asString(), 10)
            .stream()
            .filter(candidate -> candidate.serialize().equals(id))
            .findFirst()
            .orElseThrow();
        List<MessageResult> results = messageIdProbe.getMessages(messageId, PAYING_USER);
        assertThat(results).hasSize(1);
        return results.getFirst();
    }

    private static String bodyArgument(String body) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of("body", body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String html(Message draft) throws Exception {
        return lf(new MessageContentExtractor().extract(draft).getHtmlBody().orElseThrow());
    }

    /** A stored email ends its lines with CRLF, as MIME wants */
    private static String lf(String text) {
        return text.replace("\r\n", "\n");
    }

    private Message draft(String content) throws Exception {
        return parse(stored(JsonPath.from(content).getString("id")));
    }

    private static Message parse(MessageResult result) throws Exception {
        return new DefaultMessageBuilder().parseMessage(result.getFullContent().getInputStream());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCalls")
    void shouldRejectAnInvalidCall(String description, String arguments, int status, String detail) {
        postTool(TOOL, arguments)
            .statusCode(status)
            .body("detail", equalTo(detail));
    }

    static Stream<Arguments> invalidCalls() {
        return Stream.of(
            Arguments.of("shouldRejectADraftWithoutBody", "{\"to\": [\"user2@domain.tld\"]}", SC_BAD_REQUEST, "Missing 'body'"),
            Arguments.of("shouldRejectAnInvalidAddress", "{\"to\": [\"user2\"], \"body\": \"Hello\"}", SC_BAD_REQUEST, "Invalid email address: user2"));
    }
}
