package com.linagora.tmail.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import jakarta.mail.Flags;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.common.probe.JmapSettingsProbe;

import io.restassured.RestAssured;

public abstract class InboxArchivalIntegrationContract {
    protected static final Domain DOMAIN = Domain.of("domain.tld");
    protected static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    protected static final Instant VERY_OLD_INSTANT = Instant.parse("1999-12-07T01:15:30.00Z");

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        DataProbeImpl dataProbe = server.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN.asString());
        dataProbe.addUser(BOB.asString(), "password");

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxPath.inbox(BOB));

        WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .setBasePath("/mailboxes")
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
    void inboxArchivalTaskShouldWork(GuiceJamesServer server, UpdatableTickingClock clock) throws MailboxException {
        server.getProbe(JmapSettingsProbe.class)
            .reset(BOB, Map.of("inbox.archival.enabled", "true"));

        clock.setInstant(VERY_OLD_INSTANT);
        appendMessage(BOB, MailboxPath.inbox(BOB), server);

        clock.setInstant(Instant.now());
        String taskId = given()
            .queryParam("task", "InboxArchival")
            .post()
            .jsonPath()
            .getString("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("type", is("InboxArchivalTask"))
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("startedDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("additionalInformation.type", is("InboxArchivalTask"))
            .body("additionalInformation.archivedMessageCount", is(1))
            .body("additionalInformation.errorMessageCount", is(0))
            .body("additionalInformation.successfulUsersCount", is(1))
            .body("additionalInformation.failedUsersCount", is(0))
            .body("additionalInformation.failedUsers", empty());
    }
}
