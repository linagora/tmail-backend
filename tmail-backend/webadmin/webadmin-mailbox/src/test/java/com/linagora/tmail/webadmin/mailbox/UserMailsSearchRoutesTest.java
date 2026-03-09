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

package com.linagora.tmail.webadmin.mailbox;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.memory.projections.MemoryMessageFastViewProjection;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import reactor.core.publisher.Mono;

class UserMailsSearchRoutesTest {

    private static final Username BOB = Username.of("bob@example.com");
    private static final MailboxPath BOB_INBOX = MailboxPath.inbox(BOB);
    private static final String SIMPLE_MESSAGE = "From: sender@example.com\r\n"
        + "To: bob@example.com\r\n"
        + "Subject: Test subject\r\n"
        + "\r\n"
        + "Test body content";
    private static final String REASON = "Support investigation - ticket #1234";

    private WebAdminServer webAdminServer;
    private InMemoryMailboxManager mailboxManager;
    private MemoryMessageFastViewProjection fastViewProjection;
    private InMemoryIntegrationResources resources;

    @BeforeEach
    void setUp() throws Exception {
        resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        fastViewProjection = new MemoryMessageFastViewProjection(new RecordingMetricFactory());

        MailboxSession session = mailboxManager.createSystemSession(BOB);
        mailboxManager.createMailbox(BOB_INBOX, session);

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new UserMailsSearchRoutes(
                    mailboxManager,
                    resources.getMessageIdManager(),
                    fastViewProjection,
                    new InMemoryId.Factory(),
                    new JsonTransformer()))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    void searchShouldReturn200WithEmptyList() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\"}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(0));
    }

    @Test
    void searchShouldReturnMessageWhenFound() throws Exception {
        appendMessage(BOB, BOB_INBOX, SIMPLE_MESSAGE, new Flags());

        String response = given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\"}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1))
            .extract().body().asString();

        assertThatJson(response).inPath("[0].from[0].email").isEqualTo("sender@example.com");
        assertThatJson(response).inPath("[0].to[0].email").isEqualTo("bob@example.com");
        assertThatJson(response).inPath("[0].subject").isEqualTo("Test subject");
    }

    @Test
    void searchShouldReturnMessageKeywords() throws Exception {
        appendMessage(BOB, BOB_INBOX, SIMPLE_MESSAGE, new Flags(Flags.Flag.SEEN));

        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\"}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("[0].keywords.$seen", equalTo(true));
    }

    @Test
    void searchShouldReturnMessageMailboxIds() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        MailboxId inboxId = mailboxManager.getMailbox(BOB_INBOX, session).getId();
        appendMessage(BOB, BOB_INBOX, SIMPLE_MESSAGE, new Flags());

        String response = given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\"}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .extract().body().asString();

        assertThatJson(response).inPath("[0].mailboxIds." + inboxId.serialize()).isEqualTo(true);
    }

    @Test
    void searchWithTextFilterShouldReturnMatchingMessages() throws Exception {
        appendMessage(BOB, BOB_INBOX, SIMPLE_MESSAGE, new Flags());
        appendMessage(BOB, BOB_INBOX,
            "From: other@example.com\r\nTo: bob@example.com\r\nSubject: Other\r\n\r\nOther content",
            new Flags());

        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\",\"filter\":{\"subject\":\"Test subject\"}}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1));
    }

    @Test
    void searchWithFromFilterShouldReturnMatchingMessages() throws Exception {
        appendMessage(BOB, BOB_INBOX, SIMPLE_MESSAGE, new Flags());
        appendMessage(BOB, BOB_INBOX,
            "From: other@example.com\r\nTo: bob@example.com\r\nSubject: Other\r\n\r\nOther",
            new Flags());

        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\",\"filter\":{\"from\":\"sender@example.com\"}}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1));
    }

    @Test
    void searchWithHasKeywordsFilterShouldReturnOnlySeenMessages() throws Exception {
        appendMessage(BOB, BOB_INBOX, SIMPLE_MESSAGE, new Flags(Flags.Flag.SEEN));
        appendMessage(BOB, BOB_INBOX,
            "From: other@example.com\r\nTo: bob@example.com\r\nSubject: Unseen\r\n\r\nUnseen",
            new Flags());

        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\",\"filter\":{\"hasKeywords\":[\"$seen\"]}}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1));
    }

    @Test
    void searchShouldRespectLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            appendMessage(BOB, BOB_INBOX, SIMPLE_MESSAGE, new Flags());
        }

        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\"}")
        .when()
            .post("/users/bob@example.com/mails?limit=3")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(3));
    }

    @Test
    void searchShouldReturnPreviewFromFastViewProjection() throws Exception {
        MessageId messageId = appendMessage(BOB, BOB_INBOX, SIMPLE_MESSAGE, new Flags());

        String expectedPreview = "This is a preview text";
        Mono.from(fastViewProjection.store(messageId,
            MessageFastViewPrecomputedProperties.builder()
                .preview(Preview.from(expectedPreview))
                .hasAttachment(false)
                .build()))
            .block();

        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\"}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("[0].preview", equalTo(expectedPreview));
    }

    @Test
    void searchShouldReturnHasAttachmentFromFastViewProjection() throws Exception {
        MessageId messageId = appendMessage(BOB, BOB_INBOX, SIMPLE_MESSAGE, new Flags());

        Mono.from(fastViewProjection.store(messageId,
            MessageFastViewPrecomputedProperties.builder()
                .preview(Preview.from(""))
                .hasAttachment(true)
                .build()))
            .block();

        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\"}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("[0].hasAttachment", equalTo(true));
    }

    @Test
    void searchShouldReturn400WhenLimitIsInvalid() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\"}")
        .when()
            .post("/users/bob@example.com/mails?limit=invalid")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void searchShouldReturn400WhenLimitExceedsMax() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\"}")
        .when()
            .post("/users/bob@example.com/mails?limit=501")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void searchShouldReturn400WhenOffsetIsNegative() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\"}")
        .when()
            .post("/users/bob@example.com/mails?offset=-1")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void searchShouldReturn400WhenBodyIsInvalidJson() {
        given()
            .contentType(ContentType.JSON)
            .body("not-json")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void searchShouldReturn400WhenBodyIsAbsent() {
        when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", containsString("reason"));
    }

    @Test
    void searchShouldReturn400WhenReasonIsAbsent() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"filter\":{}}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", containsString("reason"));
    }

    @Test
    void searchShouldReturn400WhenReasonIsBlank() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"   \"}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", containsString("reason"));
    }

    @Test
    void searchShouldReturn400WhenSortPropertyIsInvalid() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\",\"sort\":[{\"property\":\"invalidProperty\"}]}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void searchShouldReturnMessageIdInResponse() throws Exception {
        MessageId messageId = appendMessage(BOB, BOB_INBOX, SIMPLE_MESSAGE, new Flags());

        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\"}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("[0].id", equalTo(messageId.serialize()));
    }

    @Test
    void searchShouldReturnReceivedAt() throws Exception {
        appendMessage(BOB, BOB_INBOX, SIMPLE_MESSAGE, new Flags());

        String response = given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\"}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .extract().body().asString();

        assertThatJson(response).inPath("[0].receivedAt").isPresent();
    }

    @Test
    void searchShouldSortByReceivedAtDescendingByDefault() throws Exception {
        MessageId first = appendMessage(BOB, BOB_INBOX,
            "From: a@example.com\r\nTo: bob@example.com\r\nSubject: First\r\n\r\nbody", new Flags());
        MessageId second = appendMessage(BOB, BOB_INBOX,
            "From: b@example.com\r\nTo: bob@example.com\r\nSubject: Second\r\n\r\nbody", new Flags());

        given()
            .contentType(ContentType.JSON)
            .body("{\"reason\":\"" + REASON + "\",\"sort\":[{\"property\":\"receivedAt\",\"isAscending\":false}]}")
        .when()
            .post("/users/bob@example.com/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(2))
            .body("[0].id", equalTo(second.serialize()));
    }

    private MessageId appendMessage(Username username, MailboxPath mailboxPath,
                                    String rawMessage, Flags flags) throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(username);
        MessageManager messageManager = mailboxManager.getMailbox(mailboxPath, session);
        return messageManager.appendMessage(
            new ByteArrayInputStream(rawMessage.getBytes(StandardCharsets.UTF_8)),
            new Date(),
            session,
            true,
            flags).getId().getMessageId();
    }
}
