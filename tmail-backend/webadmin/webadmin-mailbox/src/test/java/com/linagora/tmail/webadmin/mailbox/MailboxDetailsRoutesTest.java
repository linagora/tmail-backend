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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;

class MailboxDetailsRoutesTest {
    private static final Username BOB = Username.of("bob@example.com");
    private static final MailboxPath BOB_INBOX = MailboxPath.inbox(BOB);

    private WebAdminServer webAdminServer;
    private InMemoryMailboxManager mailboxManager;

    @BeforeEach
    void setUp() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new MailboxDetailsRoutes(
                    mailboxManager,
                    mailboxManager.getMapperFactory(),
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
    void getMailboxDetailsShouldReturn404WhenMailboxNotFound() {
        when()
            .get("/mailboxes/1")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void getMailboxDetailsShouldReturn400WhenInvalidMailboxId() {
        when()
            .get("/mailboxes/invalid-id")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void getMailboxDetailsShouldReturnMailboxDetailsWhenMailboxExists() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        MailboxId mailboxId = mailboxManager.createMailbox(BOB_INBOX, session).get();

        when()
            .get("/mailboxes/" + mailboxId.serialize())
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("mailboxId", equalTo(mailboxId.serialize()))
            .body("mailboxPath", equalTo(BOB_INBOX.asString()));
    }

    @Test
    void getMailboxDetailsShouldReturnMailboxIdAndPath() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        MailboxId mailboxId = mailboxManager.createMailbox(BOB_INBOX, session).get();

        when()
            .get("/mailboxes/" + mailboxId.serialize())
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("mailboxId", notNullValue())
            .body("mailboxPath", notNullValue());
    }
}
