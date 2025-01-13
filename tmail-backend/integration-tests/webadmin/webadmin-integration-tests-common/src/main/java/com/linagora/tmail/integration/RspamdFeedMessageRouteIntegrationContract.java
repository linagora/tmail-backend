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

package com.linagora.tmail.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.ByteArrayInputStream;
import java.util.Date;

import jakarta.mail.Flags;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;

public abstract class RspamdFeedMessageRouteIntegrationContract {
    protected static final Domain DOMAIN = Domain.of("domain.tld");
    protected static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    protected static final MailboxPath BOB_SPAM_MAILBOX = MailboxPath.forUser(BOB, "Spam");

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        DataProbeImpl dataProbe = server.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN.asString());
        dataProbe.addUser(BOB.asString(), "password");

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxPath.inbox(BOB));
        mailboxProbe.createMailbox(BOB_SPAM_MAILBOX);


        WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .setBasePath("/rspamd")
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }


    private void appendMessage(Username username, MailboxPath mailboxPath, GuiceJamesServer server) throws MailboxException {
        server.getProbe(MailboxProbeImpl.class)
            .appendMessage(username.asString(),
                mailboxPath,
                new ByteArrayInputStream(String.format("random content %4.3f", Math.random()).getBytes()),
                new Date(), true, new Flags());
    }

    @Test
    void feedSpamShouldBeExposed() {
        given()
            .queryParam("action", "reportSpam")
            .post()
            .then()
            .statusCode(201)
            .body("taskId", notNullValue());
    }

    @Test
    void feedSpamTaskShouldWork(GuiceJamesServer server) throws MailboxException {
        appendMessage(BOB, BOB_SPAM_MAILBOX, server);

        String taskId = given()
            .queryParam("action", "reportSpam")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .when()
            .get(taskId + "/await")
            .then()
            .body("status", is("completed"))
            .body("additionalInformation.type", is("FeedSpamToRspamdTask"))
            .body("additionalInformation.spamMessageCount", is(1))
            .body("additionalInformation.reportedSpamMessageCount", is(1))
            .body("additionalInformation.errorCount", is(0))
            .body("additionalInformation.runningOptions.messagesPerSecond", is(10))
            .body("additionalInformation.runningOptions.periodInSecond", is(nullValue()))
            .body("additionalInformation.runningOptions.samplingProbability", is((float) 1));
    }


    @Test
    void feedHamShouldBeExposed() {
        given()
            .queryParam("action", "reportHam")
            .post()
            .then()
            .statusCode(201)
            .body("taskId", notNullValue());
    }

    @Test
    void feedHamTaskShouldWork(GuiceJamesServer server) throws MailboxException {
        appendMessage(BOB, MailboxPath.inbox(BOB), server);

        String taskId = given()
            .queryParam("action", "reportHam")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .when()
            .get(taskId + "/await")
            .then()
            .body("status", is("completed"))
            .body("additionalInformation.type", is("FeedHamToRspamdTask"))
            .body("additionalInformation.hamMessageCount", is(1))
            .body("additionalInformation.reportedHamMessageCount", is(1))
            .body("additionalInformation.errorCount", is(0))
            .body("additionalInformation.runningOptions.messagesPerSecond", is(10))
            .body("additionalInformation.runningOptions.periodInSecond", is(nullValue()))
            .body("additionalInformation.runningOptions.samplingProbability", is((float) 1));
    }
}
