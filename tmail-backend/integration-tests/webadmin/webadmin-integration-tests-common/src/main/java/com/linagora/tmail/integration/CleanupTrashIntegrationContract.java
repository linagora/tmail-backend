package com.linagora.tmail.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import javax.mail.Flags;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.common.probe.JmapSettingsProbe;
import com.linagora.tmail.james.jmap.settings.JmapSettings;

import io.restassured.RestAssured;

public abstract class CleanupTrashIntegrationContract {

    private static final String BASE_PATH = Constants.SEPARATOR + "mailboxes";
    protected static final Domain DOMAIN = Domain.of("domain.tld");
    protected static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    protected static final Instant VERY_OLD_INSTANT = Instant.parse("1999-12-07T01:15:30.00Z");

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        DataProbeImpl dataProbe = server.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN.asString());
        dataProbe.addUser(BOB.asString(), "password");

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.TRASH));

        WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .setBasePath(BASE_PATH)
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void cleanupTrashShouldBeExposed() {
        given()
            .queryParam("task", "CleanupTrash")
            .post()
        .then()
            .statusCode(201)
            .body("taskId", notNullValue());
    }

    @Test
    void cleanupTrashTaskShouldWork(GuiceJamesServer server, UpdatableTickingClock clock) throws Exception {
        server.getProbe(JmapSettingsProbe.class)
            .reset(BOB, Map.of(JmapSettings.TrashCleanupEnabledSetting().asString(),
                "true",
                JmapSettings.TrashCleanupPeriodSetting().asString(),
                JmapSettings.WeeklyPeriod()));

        clock.setInstant(VERY_OLD_INSTANT);
        appendMessage(BOB, MailboxPath.forUser(BOB, DefaultMailboxes.TRASH), server);

        clock.setInstant(Instant.now());

        String taskId = given()
            .queryParam("task", "CleanupTrash")
            .post()
            .jsonPath()
            .getString("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("startedDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("type", is("cleanup-trash"))
            .body("additionalInformation.timestamp", is(notNullValue()))
            .body("additionalInformation.type", is("cleanup-trash"))
            .body("additionalInformation.processedUsersCount", is(1))
            .body("additionalInformation.deletedMessagesCount", is(1))
            .body("additionalInformation.failedUsers", is(empty()));
    }

    @Test
    void cleanupTrashTaskShouldFailWhenQueryParaValueIsInvalid() {
        given()
            .queryParam("task", "CleanupTrash")
            .queryParam("usersPerSecond", "abc")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Illegal value supplied for query parameter 'usersPerSecond', expecting a strictly positive optional integer"));
    }

    private void appendMessage(Username username, MailboxPath mailboxPath, GuiceJamesServer server) throws MailboxException {
        server.getProbe(MailboxProbeImpl.class)
            .appendMessage(username.asString(),
                mailboxPath,
                new ByteArrayInputStream(String.format("random content %4.3f", Math.random()).getBytes()),
                new Date(), true, new Flags());
    }
}
