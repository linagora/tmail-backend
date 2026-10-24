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

    private static final String TOOL = "search_emails";

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = server();

    private MessageId invoice;
    private MessageId meeting;
    private MessageId archivedReport;

    @BeforeEach
    void setUpEmails() throws Exception {
        invoice = append(INBOX,
            email("Alice <alice@domain.tld>", "paying@domain.tld", "Invoice October", "Please find the invoice"),
            Instant.parse("2026-10-10T09:00:00Z"));
        meeting = append(INBOX,
            email("bob@domain.tld", "paying@domain.tld", "Meeting", "See you at the meeting room"),
            Instant.parse("2026-10-12T09:00:00Z"), new Flags(Flags.Flag.SEEN));
        archivedReport = append(ARCHIVE,
            email("alice@domain.tld", "carol@domain.tld", "Report", "The yearly report"),
            Instant.parse("2026-10-14T09:00:00Z"));
    }

    @Test
    void shouldReturnEveryEmailMostRecentFirstWithoutFilter() {
        String content = callTool("search_emails", "{}");

        assertThat(strings(content, "emails.id"))
            .containsExactly(archivedReport.serialize(), meeting.serialize(), invoice.serialize());
    }

    @Test
    void shouldSummarizeTheEmails() {
        String content = callTool("search_emails", "{\"from\": \"bob@domain.tld\"}");

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
        String content = callTool("search_emails", "{\"from\": \"alice@domain.tld\"}");

        assertThat(strings(content, "emails.id"))
            .containsExactly(archivedReport.serialize(), invoice.serialize());
    }

    @Test
    void shouldFilterByRecipient() {
        String content = callTool("search_emails", "{\"to\": \"carol@domain.tld\"}");

        assertThat(strings(content, "emails.id")).containsExactly(archivedReport.serialize());
    }

    @Test
    void shouldFilterByTextInTheSubject() {
        String content = callTool("search_emails", "{\"text\": \"invoice\"}");

        assertThat(strings(content, "emails.id")).containsExactly(invoice.serialize());
    }

    @Test
    void shouldFilterByTextInTheBody() {
        String content = callTool("search_emails", "{\"text\": \"yearly\"}");

        assertThat(strings(content, "emails.id")).containsExactly(archivedReport.serialize());
    }

    @Test
    void shouldFilterUnreadEmails() {
        String content = callTool("search_emails", "{\"unread\": true}");

        assertThat(strings(content, "emails.id"))
            .containsExactly(archivedReport.serialize(), invoice.serialize());
    }

    @Test
    void shouldFilterReadEmailsWithABooleanSentAsAString() {
        String content = callTool("search_emails", "{\"unread\": \"false\"}");

        assertThat(strings(content, "emails.id")).containsExactly(meeting.serialize());
    }

    @Test
    void shouldFilterByDates() {
        String content = callTool("search_emails", "{\"after\": \"2026-10-11\", \"before\": \"2026-10-13T00:00:00Z\"}");

        assertThat(strings(content, "emails.id")).containsExactly(meeting.serialize());
    }

    @Test
    void shouldFilterByMailboxes() {
        String content = callTool("search_emails", "{\"inMailboxes\": [\"" + archiveId.serialize() + "\"]}");

        assertThat(strings(content, "emails.id")).containsExactly(archivedReport.serialize());
    }

    @Test
    void shouldCombineTheFilters() {
        String content = callTool("search_emails", "{\"from\": \"alice@domain.tld\", \"inMailboxes\": [\"" + inboxId.serialize() + "\"]}");

        assertThat(strings(content, "emails.id")).containsExactly(invoice.serialize());
    }

    @Test
    void shouldLimitTheResults() {
        String content = callTool("search_emails", "{\"limit\": \"1\"}");

        assertThat(strings(content, "emails.id")).containsExactly(archivedReport.serialize());
    }

    @Test
    void shouldNotFindTheEmailsOfAnotherUser() throws Exception {
        append(MailboxPath.inbox(NON_PAYING_USER), email("dave@domain.tld", "nonpaying@domain.tld", "Private", "Secret"),
            Instant.parse("2026-10-15T09:00:00Z"));

        String content = callTool("search_emails", "{\"from\": \"dave@domain.tld\"}");

        assertThatJson(content).node("emails").isArray().isEmpty();
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
            Arguments.of("shouldRejectAMalformedDate", "{\"after\": \"last week\"}", SC_BAD_REQUEST, "'after' must be an ISO 8601 date"),
            Arguments.of("shouldRejectAnUnreadFilterThatIsNotABoolean", "{\"unread\": \"maybe\"}", SC_BAD_REQUEST, "'unread' must be a boolean"),
            Arguments.of("shouldRejectMailboxesThatAreNotStrings", "{\"inMailboxes\": [1, 2]}", SC_BAD_REQUEST, "'inMailboxes' must be an array of strings"));
    }
}
