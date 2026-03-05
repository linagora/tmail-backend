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

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;

class MessageLocationRoutesTest {
    private static final Username BOB = Username.of("bob@example.com");
    private static final MailboxPath BOB_INBOX = MailboxPath.inbox(BOB);
    private static final MailboxPath BOB_SENT = MailboxPath.forUser(BOB, "Sent");

    private WebAdminServer webAdminServer;
    private InMemoryMailboxManager mailboxManager;

    @BeforeEach
    void setUp() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new MessageLocationRoutes(
                    mailboxManager,
                    mailboxManager.getMapperFactory(),
                    new InMemoryMessageId.Factory(),
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
    void getMessageLocationShouldReturn404WhenMessageNotFound() {
        when()
            .get("/messages/1")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void getMessageLocationShouldReturn400WhenInvalidMessageId() {
        when()
            .get("/messages/invalid-id")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void getMessageLocationShouldReturnMailboxPathWhenMessageExists() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        mailboxManager.createMailbox(BOB_INBOX, session);

        ComposedMessageId composedMessageId = mailboxManager.getMailbox(BOB_INBOX, session)
            .appendMessage(
                MessageManager.AppendCommand.builder().build("Subject: test\r\n\r\nbody"),
                session)
            .getId();

        when()
            .get("/messages/" + composedMessageId.getMessageId().serialize())
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("mailboxes", hasSize(1))
            .body("mailboxes[0].mailboxPath", org.hamcrest.Matchers.equalTo(BOB_INBOX.asString()))
            .body("mailboxes[0].mailboxId", notNullValue());
    }

    @Test
    void getMessageLocationShouldReturnAllMailboxesWhenMessageIsInMultipleMailboxes() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        mailboxManager.createMailbox(BOB_INBOX, session);
        mailboxManager.createMailbox(BOB_SENT, session);

        ComposedMessageId composedMessageId = mailboxManager.getMailbox(BOB_INBOX, session)
            .appendMessage(
                MessageManager.AppendCommand.builder().build("Subject: test\r\n\r\nbody"),
                session)
            .getId();

        // Copy the message to Sent
        mailboxManager.copyMessages(
            org.apache.james.mailbox.model.MessageRange.one(composedMessageId.getUid()),
            BOB_INBOX,
            BOB_SENT,
            session);

        when()
            .get("/messages/" + composedMessageId.getMessageId().serialize())
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("mailboxes", hasSize(2));
    }
}
