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

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.james.jmap.rfc8621.contract.Fixture.authScheme;
import static org.apache.james.jmap.rfc8621.contract.Fixture.baseRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.mail.Flags;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ListEmailsToolTest extends AiToolsTestBase {

    private static final String TOOL = "list_emails";

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = server();

    private static final Instant MONDAY = Instant.parse("2026-10-12T09:00:00Z");
    private static final Instant TUESDAY = Instant.parse("2026-10-13T09:00:00Z");
    private static final Instant WEDNESDAY = Instant.parse("2026-10-14T09:00:00Z");

    @Test
    void shouldListTheInboxMostRecentFirst() throws Exception {
        MessageId monday = append(INBOX, email("Alice <alice@domain.tld>", "paying@domain.tld", "Monday", "First email"), MONDAY);
        MessageId wednesday = append(INBOX, email("bob@domain.tld", "paying@domain.tld", "Wednesday", "Third email"),
            WEDNESDAY, new Flags(Flags.Flag.SEEN));
        MessageId tuesday = append(INBOX, email("alice@domain.tld", "paying@domain.tld", "Tuesday", "Second email"), TUESDAY);

        String content = callTool("list_emails", "{}");

        assertThatJson(content).isEqualTo("""
            {
              "mailboxId": "%s",
              "page": 1,
              "limit": 10,
              "emails": [
                {
                  "id": "%s",
                  "from": "bob@domain.tld",
                  "subject": "Wednesday",
                  "receivedAt": "2026-10-14T09:00:00Z",
                  "unread": false,
                  "preview": "Third email"
                },
                {
                  "id": "%s",
                  "from": "alice@domain.tld",
                  "subject": "Tuesday",
                  "receivedAt": "2026-10-13T09:00:00Z",
                  "unread": true,
                  "preview": "Second email"
                },
                {
                  "id": "%s",
                  "from": "Alice <alice@domain.tld>",
                  "subject": "Monday",
                  "receivedAt": "2026-10-12T09:00:00Z",
                  "unread": true,
                  "preview": "First email"
                }
              ]
            }""".formatted(inboxId.serialize(), wednesday.serialize(), tuesday.serialize(), monday.serialize()));
    }

    @Test
    void shouldListAnotherMailbox() throws Exception {
        append(INBOX, email("alice@domain.tld", "paying@domain.tld", "In the inbox", "Body"), MONDAY);
        MessageId archived = append(ARCHIVE, email("alice@domain.tld", "paying@domain.tld", "Archived", "Body"), TUESDAY);

        String content = callTool("list_emails", "{\"mailboxId\": \"" + archiveId.serialize() + "\"}");

        assertThat(JsonPath.from(content).getString("mailboxId")).isEqualTo(archiveId.serialize());
        assertThat(strings(content, "emails.id")).containsExactly(archived.serialize());
    }

    @Test
    void shouldReturnTheRequestedPage() throws Exception {
        MessageId monday = append(INBOX, email("alice@domain.tld", "paying@domain.tld", "Monday", "Body"), MONDAY);
        MessageId tuesday = append(INBOX, email("alice@domain.tld", "paying@domain.tld", "Tuesday", "Body"), TUESDAY);
        append(INBOX, email("alice@domain.tld", "paying@domain.tld", "Wednesday", "Body"), WEDNESDAY);

        String content = callTool("list_emails", "{\"page\": 2, \"limit\": 2}");

        assertThatJson(content).node("page").isEqualTo(2);
        assertThat(strings(content, "emails.id")).containsExactly(monday.serialize());
        assertThat(strings(callTool("list_emails", "{\"page\": 1, \"limit\": 2}"), "emails.id"))
            .element(1).isEqualTo(tuesday.serialize());
    }

    @Test
    void shouldReadNumbersSentAsStrings() throws Exception {
        append(INBOX, email("alice@domain.tld", "paying@domain.tld", "Monday", "Body"), MONDAY);
        MessageId tuesday = append(INBOX, email("alice@domain.tld", "paying@domain.tld", "Tuesday", "Body"), TUESDAY);

        String content = callTool("list_emails", "{\"page\": \"1\", \"limit\": \"1\"}");

        assertThatJson(content).node("limit").isEqualTo(1);
        assertThat(strings(content, "emails.id")).containsExactly(tuesday.serialize());
    }

    @Test
    void shouldCapTheLimit() throws Exception {
        for (int day = 1; day <= 21; day++) {
            append(INBOX, email("alice@domain.tld", "paying@domain.tld", "Email " + day, "Body"),
                Instant.parse("2026-09-01T09:00:00Z").plusSeconds(day * 86400L));
        }

        String content = callTool("list_emails", "{\"limit\": 50}");

        assertThatJson(content).node("limit").isEqualTo(20);
        assertThatJson(content).node("emails").isArray().hasSize(20);
    }

    @Test
    void shouldShortenThePreview() throws Exception {
        append(INBOX, email("alice@domain.tld", "paying@domain.tld", "Long", "word ".repeat(100)), MONDAY);

        String content = callTool("list_emails", "{}");

        assertThat(JsonPath.from(content).getString("emails[0].preview"))
            .startsWith("word word")
            .hasSizeLessThanOrEqualTo(Preview.DEFAULT_MAX_LENGTH);
    }

    @Test
    void shouldGiveThePreviewStoredByJames() throws Exception {
        MessageId id = append(INBOX, email("alice@domain.tld", "paying@domain.tld", "Monday", "First email"), MONDAY);
        fastViewProbe.store(id, "The stored preview");

        String content = callTool("list_emails", "{}");

        assertThat(JsonPath.from(content).getString("emails[0].preview")).isEqualTo("The stored preview");
    }

    @Test
    void shouldComputeAndStoreAMissingPreview() throws Exception {
        MessageId id = append(INBOX, email("alice@domain.tld", "paying@domain.tld", "Monday", "First email"), MONDAY);
        fastViewProbe.delete(id);

        String content = callTool("list_emails", "{}");

        assertThat(JsonPath.from(content).getString("emails[0].preview")).isEqualTo("First email");
        Awaitility.await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(fastViewProbe.preview(id)).contains("First email"));
    }

    @Test
    void shouldNotListTheMailboxOfAnotherUser() throws Exception {
        append(MailboxPath.inbox(NON_PAYING_USER), email("alice@domain.tld", "nonpaying@domain.tld", "Private", "Secret"), MONDAY);
        String otherInbox = mailboxProbe.getMailboxId(MailboxPath.inbox(NON_PAYING_USER).getNamespace(),
            NON_PAYING_USER.asString(), MailboxPath.inbox(NON_PAYING_USER).getName()).serialize();

        postTool("list_emails", "{\"mailboxId\": \"" + otherInbox + "\"}")
            .statusCode(SC_NOT_FOUND)
            .body("detail", equalTo("Mailbox not found"));
    }

    @Test
    void shouldListNothingForAUserWithoutInbox(GuiceJamesServer jamesServer) throws Exception {
        Username newcomer = Username.fromLocalPartWithDomain("newcomer", DOMAIN);
        addEligibleUser(jamesServer, newcomer);

        String content = given(baseRequestSpecBuilder(jamesServer)
                .setBasePath(AI_TOOLS_ENDPOINT)
                .setAuth(authScheme(new UserCredential(newcomer, PASSWORD)))
                .build())
            .contentType(ContentType.JSON)
            .body(toolCall("list_emails", "{}"))
        .when()
            .post()
        .then()
            .statusCode(SC_OK)
            .extract()
            .jsonPath()
            .getString("content");

        assertThatJson(content).isEqualTo("{\"mailboxId\": null, \"page\": 1, \"limit\": 10, \"emails\": []}");
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
            Arguments.of("shouldNotListAnUnknownMailbox", "{\"mailboxId\": \"999\"}", SC_NOT_FOUND, "Mailbox not found"),
            Arguments.of("shouldRejectAPageAboveTheMaximum", "{\"page\": 51}", SC_BAD_REQUEST, "'page' must be at most 50"),
            Arguments.of("shouldRejectANegativeLimit", "{\"limit\": -1}", SC_BAD_REQUEST, "'limit' must be positive"),
            Arguments.of("shouldRejectAPageBelowOne", "{\"page\": 0}", SC_BAD_REQUEST, "'page' must be positive"),
            Arguments.of("shouldRejectALimitThatIsNotANumber", "{\"limit\": \"ten\"}", SC_BAD_REQUEST, "'limit' must be an integer"),
            Arguments.of("shouldRejectAMalformedMailboxId", "{\"mailboxId\": \"not-an-id\"}", SC_BAD_REQUEST, "Invalid mailbox id: not-an-id"));
    }
}
