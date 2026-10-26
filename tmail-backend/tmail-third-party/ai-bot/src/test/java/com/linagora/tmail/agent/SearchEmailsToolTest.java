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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.Instant;
import java.util.stream.Stream;

import jakarta.mail.Flags;
import org.apache.james.JamesServerExtension;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SearchEmailsToolTest extends AiToolsTestBase {

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = server();

    private MessageId invoice;
    private MessageId meeting;
    private MessageId archivedReport;

    @BeforeEach
    void setUpEmails() throws Exception {
        invoice = append(INBOX,
            email("Alice <alice@domain.tld>", "Invoice October", "Please find the invoice"),
            Instant.parse("2026-10-10T09:00:00Z"));
        meeting = append(INBOX,
            email("bob@domain.tld", "Meeting", "See you at the meeting room"),
            Instant.parse("2026-10-12T09:00:00Z"), new Flags(Flags.Flag.SEEN));
        archivedReport = append(ARCHIVE,
            email("alice@domain.tld", "Report", "The yearly report").setTo("carol@domain.tld"),
            Instant.parse("2026-10-14T09:00:00Z"));
    }

    @Test
    void shouldReturnEveryEmailMostRecentFirstWithoutFilter() {
        String content = callTool(SEARCH_EMAILS, "{}");

        assertThat(emailIds(content))
            .containsExactly(archivedReport.serialize(), meeting.serialize(), invoice.serialize());
    }

    @Test
    void shouldSummarizeTheEmails() {
        String content = callTool(SEARCH_EMAILS, "{\"from\": \"bob@domain.tld\"}");

        assertThatJson(content).isEqualTo("""
            {
              "emails": [{
                "id": "%s",
                "from": "bob@domain.tld",
                "subject": "Meeting",
                "receivedAt": "2026-10-12T09:00:00Z",
                "unread": false,
                "preview": "See you at the meeting room"
              }]
            }""".formatted(meeting.serialize()));
    }

    @Test
    void shouldFilterBySender() {
        String content = callTool(SEARCH_EMAILS, "{\"from\": \"alice@domain.tld\"}");

        assertThat(emailIds(content))
            .containsExactly(archivedReport.serialize(), invoice.serialize());
    }

    @Test
    void shouldFilterByRecipient() {
        String content = callTool(SEARCH_EMAILS, "{\"to\": \"carol@domain.tld\"}");

        assertThat(emailIds(content)).containsExactly(archivedReport.serialize());
    }

    @Test
    void shouldFilterByTextInTheSubject() {
        String content = callTool(SEARCH_EMAILS, "{\"text\": \"invoice\"}");

        assertThat(emailIds(content)).containsExactly(invoice.serialize());
    }

    @Test
    void shouldFilterByTextInTheBody() {
        String content = callTool(SEARCH_EMAILS, "{\"text\": \"yearly\"}");

        assertThat(emailIds(content)).containsExactly(archivedReport.serialize());
    }

    @Test
    void shouldFilterUnreadEmails() {
        String content = callTool(SEARCH_EMAILS, "{\"unread\": true}");

        assertThat(emailIds(content))
            .containsExactly(archivedReport.serialize(), invoice.serialize());
    }

    @Test
    void shouldFilterReadEmailsWithABooleanSentAsAString() {
        String content = callTool(SEARCH_EMAILS, "{\"unread\": \"false\"}");

        assertThat(emailIds(content)).containsExactly(meeting.serialize());
    }

    @Test
    void shouldFilterByDates() {
        String content = callTool(SEARCH_EMAILS, "{\"after\": \"2026-10-11\", \"before\": \"2026-10-13T00:00:00Z\"}");

        assertThat(emailIds(content)).containsExactly(meeting.serialize());
    }

    @Test
    void shouldFilterByMailboxes() {
        String content = callTool(SEARCH_EMAILS, "{\"inMailboxes\": [\"" + archiveId.serialize() + "\"]}");

        assertThat(emailIds(content)).containsExactly(archivedReport.serialize());
    }

    @Test
    void shouldCombineTheFilters() {
        String content = callTool(SEARCH_EMAILS, "{\"from\": \"alice@domain.tld\", \"inMailboxes\": [\"" + inboxId.serialize() + "\"]}");

        assertThat(emailIds(content)).containsExactly(invoice.serialize());
    }

    @Test
    void shouldLimitTheResults() {
        String content = callTool(SEARCH_EMAILS, "{\"limit\": \"1\"}");

        assertThat(emailIds(content)).containsExactly(archivedReport.serialize());
    }

    @Test
    void shouldNotFindTheEmailsOfAnotherUser() throws Exception {
        append(MailboxPath.inbox(NON_PAYING_USER), email("dave@domain.tld", "Private", "Secret").setTo("nonpaying@domain.tld"),
            Instant.parse("2026-10-15T09:00:00Z"));

        String content = callTool(SEARCH_EMAILS, "{\"from\": \"dave@domain.tld\"}");

        assertThatJson(content).node("emails").isArray().isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCalls")
    void shouldRejectAnInvalidCall(String description, String arguments, int status, String detail) {
        postTool(SEARCH_EMAILS, arguments)
            .statusCode(status)
            .body("detail", equalTo(detail));
    }

    static Stream<Arguments> invalidCalls() {
        return Stream.of(
            Arguments.of("shouldRejectAMalformedDate", "{\"after\": \"last week\"}", SC_BAD_REQUEST, "'after' must be an ISO 8601 date"),
            Arguments.of("shouldRejectAnUnreadFilterThatIsNotABoolean", "{\"unread\": \"maybe\"}", SC_BAD_REQUEST, "'unread' must be a boolean"),
            Arguments.of("shouldRejectMailboxesThatAreNotStrings", "{\"inMailboxes\": [1, 2]}", SC_BAD_REQUEST, "'inMailboxes' must be an array of strings"));
    }
}
