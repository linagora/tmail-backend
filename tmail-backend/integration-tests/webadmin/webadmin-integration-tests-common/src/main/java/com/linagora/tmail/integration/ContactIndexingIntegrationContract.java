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

import static com.linagora.tmail.integration.TestFixture.CALMLY_AWAIT;
import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.jmap.JMAPTestingConstants.jmapRequestSpecBuilder;
import static org.apache.james.jmap.JmapRFCCommonRequests.ACCEPT_JMAP_RFC_HEADER;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.awaitility.Durations;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import net.javacrumbs.jsonunit.core.Option;

public abstract class ContactIndexingIntegrationContract {
    protected Domain domain;
    protected Username bob;
    protected static final String BOB_PASSWORD = "password";
    protected Username andre;
    protected Username cedric;
    protected Username david;
    protected MailboxPath bobSentMailbox;
    protected MailboxPath andreSentMailbox;

    protected RequestSpecification webAdminApiSpec;
    protected RequestSpecification jmapSpec;

    protected String bobAccountId;

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        domain = Domain.of("contacts-" + UUID.randomUUID() + ".linagora.com");
        bob = Username.fromLocalPartWithDomain("bob", domain);
        andre = Username.fromLocalPartWithDomain("andre", domain);
        cedric = Username.fromLocalPartWithDomain("cedric", domain);
        david = Username.fromLocalPartWithDomain("david", domain);
        bobSentMailbox = MailboxPath.forUser(bob, "Sent");
        andreSentMailbox = MailboxPath.forUser(andre, "Sent");

        DataProbeImpl dataProbe = server.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(domain.asString());
        dataProbe.addUser(bob.asString(), BOB_PASSWORD);
        dataProbe.addUser(andre.asString(), "password");

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(bobSentMailbox);
        mailboxProbe.createMailbox(andreSentMailbox);

        WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);

        webAdminApiSpec = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .setBasePath("/mailboxes")
            .build();

        jmapSpec = jmapRequestSpecBuilder
            .setPort(server.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
            .addHeader(ACCEPT_JMAP_RFC_HEADER.getName(), ACCEPT_JMAP_RFC_HEADER.getValue())
            .setBasePath("/jmap")
            .build();

        bobAccountId = given(jmapSpec)
            .auth().basic(bob.asString(), BOB_PASSWORD)
            .header(ACCEPT_JMAP_RFC_HEADER)
            .get("/session")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .extract()
            .body()
            .path("primaryAccounts[\"urn:ietf:params:jmap:core\"]");

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown(GuiceJamesServer server) throws Exception {
        DataProbeImpl dataProbe = server.getProbe(DataProbeImpl.class);
        dataProbe.removeUser(bob.asString());
        dataProbe.removeUser(andre.asString());
        dataProbe.removeDomain(domain.asString());
    }

    @Test
    void contactIndexingTaskShouldExposedInWebAdmin() {
        // When create task to index contacts
        String taskId = given(webAdminApiSpec)
            .queryParam("task", "ContactIndexing")
            .post()
            .jsonPath()
            .getString("taskId");

        // Then the task is exposed in webadmin
        given(webAdminApiSpec)
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("type", is("ContactIndexing"))
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("startedDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("additionalInformation.type", is("ContactIndexing"))
            .body("additionalInformation.processedUsersCount", is(2))
            .body("additionalInformation.indexedContactsCount", is(0))
            .body("additionalInformation.failedContactsCount", is(0))
            .body("additionalInformation.failedUsers", empty());
    }

    @Test
    public void contactIndexingTaskShouldIndexContact(GuiceJamesServer server) throws Exception {
        // Given some contacts.
        // Bob: Andre, Cedric, David
        // Andre: andretwo
        server.getProbe(MailboxProbeImpl.class)
            .appendMessage(bob.asString(), bobSentMailbox, appendCommandTO(andre.asString(), cedric.asString()));
        server.getProbe(MailboxProbeImpl.class)
            .appendMessage(bob.asString(), bobSentMailbox, appendCommandTO(david.asString()));
        server.getProbe(MailboxProbeImpl.class)
            .appendMessage(andre.asString(), andreSentMailbox, appendCommandTO("andretwo@" + domain.asString()));
        Thread.sleep(1000);

        String autoCompleteQueryRequest = """
            {
              "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
              "methodCalls": [[
                "TMailContact/autocomplete",
                {
                  "accountId": "%s",
                  "filter": {"text":"andre"}
                },
                "c1"]]
            }""".formatted(bobAccountId);

        // Verify that the andre contact is not indexed
        given(jmapSpec)
            .auth().basic(bob.asString(), BOB_PASSWORD)
            .header(ACCEPT_JMAP_RFC_HEADER)
            .body(autoCompleteQueryRequest)
            .post()
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("methodResponses[0][1].list", hasSize(0));

        // When create task to index contacts
        String taskId = given(webAdminApiSpec)
            .queryParam("task", "ContactIndexing")
            .post()
            .jsonPath()
            .getString("taskId");

        // Then the task is completed
        given(webAdminApiSpec)
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("type", is("ContactIndexing"))
            .body("status", is("completed"))
            .body("additionalInformation.processedUsersCount", is(2))
            .body("additionalInformation.indexedContactsCount", is(4))
            .body("additionalInformation.failedContactsCount", is(0))
            .body("additionalInformation.failedUsers", empty());

        // Verify that the read model has caught up with the completed indexing task
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                String response = given(jmapSpec)
                    .auth().basic(bob.asString(), BOB_PASSWORD)
                    .header(ACCEPT_JMAP_RFC_HEADER)
                    .body(autoCompleteQueryRequest)
                    .post()
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .extract()
                    .body()
                    .asString();

                assertThatJson(response)
                    .when(Option.IGNORING_ARRAY_ORDER)
                    .inPath("$.methodResponses[0][1].list")
                    .isEqualTo("""
                        [
                            {
                                "id": "${json-unit.ignore}",
                                "emailAddress": "andre@%s",
                                "firstname": "",
                                "surname": ""
                            }
                        ]""".formatted(domain.asString()));
            });
    }

    protected MessageManager.AppendCommand appendCommandTO(String... to) {
        return Throwing.supplier(() -> MessageManager.AppendCommand.from(Message.Builder.of()
            .setSubject(UUID.randomUUID().toString())
            .setTo(to)
            .setBody("testmail", StandardCharsets.UTF_8)
            .build())).get();
    }
}
