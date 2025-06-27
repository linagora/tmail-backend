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

package com.linagora.tmail.integration.distributed;

import static io.restassured.RestAssured.given;
import static org.apache.james.jmap.JmapRFCCommonRequests.ACCEPT_JMAP_RFC_HEADER;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.Optional;
import java.util.UUID;

import org.apache.james.ClockExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.util.Modules;
import com.linagora.tmail.OpenPaasModule;
import com.linagora.tmail.OpenPaasTestModule;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.dav.CardDavUtils;
import com.linagora.tmail.dav.DavServerExtension;
import com.linagora.tmail.dav.WireMockOpenPaaSServerExtension;
import com.linagora.tmail.integration.ContactIndexingIntegrationContract;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedOpenpaasContactIndexingIntegrationTest extends ContactIndexingIntegrationContract {

    @RegisterExtension
    static WireMockOpenPaaSServerExtension openPaasServerExtension = new WireMockOpenPaaSServerExtension();

    @RegisterExtension
    static DavServerExtension davServerExtension = new DavServerExtension();

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .noSecondaryS3BlobStore()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new ClockExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(Modules.override(new OpenPaasModule(), new OpenPaasModule.DavModule())
                .with(new OpenPaasTestModule(openPaasServerExtension, Optional.of(davServerExtension.getDavConfiguration()), Optional.empty()))))
        .build();

    @Override
    @Disabled("This is responsibility of the OpenPaas server")
    @Test
    public void contactIndexingTaskShouldIndexContact(GuiceJamesServer server) throws Exception {
    }

    @Test
    void contactIndexingTaskShouldCreateCardDavContact(GuiceJamesServer server) throws Exception {
        // Given a message in bob sent mailbox. With an email to andre
        server.getProbe(MailboxProbeImpl.class)
            .appendMessage(BOB.asString(), BOB_SENT_MAILBOX, appendCommandTO(ANDRE.asString()));

        // Set up the scenario for openpaas & carddav extensions
        String bobOpenPassUid = UUID.randomUUID().toString();
        String andreContactUid = CardDavUtils.createContactUid(ANDRE.asMailAddress());

        openPaasServerExtension.setSearchEmailExist(BOB.asString(), bobOpenPassUid);
        davServerExtension.setCollectedContactExists(BOB.asString(), bobOpenPassUid, andreContactUid, false);
        davServerExtension.setCreateCollectedContact(BOB.asString(), bobOpenPassUid, andreContactUid);

        // Verify that the andre contact is not indexed
        given(jmapSpec)
            .auth().basic(BOB.asString(), BOB_PASSWORD)
            .header(ACCEPT_JMAP_RFC_HEADER)
            .body("""
            {
              "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
              "methodCalls": [[
                "TMailContact/autocomplete",
                {
                  "accountId": "%s",
                  "filter": {"text":"andre"}
                },
                "c1"]]
            }""".formatted(bobAccountId))
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
            .body("additionalInformation.indexedContactsCount", is(1))
            .body("additionalInformation.failedContactsCount", is(0))
            .body("additionalInformation.failedUsers", empty());

        // Verify that the andre contact was created in carddav
        davServerExtension.assertCreateCollectedContactWasCalled(BOB.asString(), bobOpenPassUid, andreContactUid, 1);
    }
}
