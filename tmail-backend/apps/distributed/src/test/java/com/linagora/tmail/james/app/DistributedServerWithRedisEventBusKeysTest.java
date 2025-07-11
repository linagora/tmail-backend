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

package com.linagora.tmail.james.app;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerConcreteContract;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.healthcheck.ResultStatus;
import org.apache.james.events.RabbitMQAndRedisEventBus;
import org.apache.james.jmap.JmapJamesServerContract;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.events.MessageMoveEvent;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import io.restassured.RestAssured;

class DistributedServerWithRedisEventBusKeysTest implements JamesServerConcreteContract, JmapJamesServerContract {
    @RegisterExtension
    static JamesServerExtension testExtension =  new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .noSecondaryS3BlobStore()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .enableSingleSave())
            .searchConfiguration(SearchConfiguration.openSearch())
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .build())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(RabbitMQAndRedisEventBusProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class)))
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @Disabled("POP3 server is disabled")
    @Test
    public void connectPOP3ServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) {
        // POP3 server is disabled
    }

    @Disabled("LMTP server is disabled")
    @Test
    public void connectLMTPServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) {
        // LMTP server is disabled
    }

    @Test
    public void shouldUseCassandraMailboxManager(GuiceJamesServer jamesServer) {
        assertThat(jamesServer.getProbe(MailboxManagerClassProbe.class).getMailboxManagerClass())
            .isEqualTo(CassandraMailboxManager.class);
    }

    @Test
    public void shouldUseCassandraUsersDAOAsDefault(GuiceJamesServer jamesServer) {
        assertThat(jamesServer.getProbe(UsersRepositoryClassProbe.class).getUsersDAOClass())
            .isEqualTo(CassandraUsersDAO.class);
    }

    @Test
    public void rabbitEventBusConsumerHealthCheckShouldWork(GuiceJamesServer jamesServer) {
        WebAdminGuiceProbe probe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(probe.getWebAdminPort()).build();

        given()
        .when()
            .get("/healthcheck")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", equalTo(ResultStatus.HEALTHY.getValue()))
            .body("checks.componentName", hasItems("EventbusConsumers-mailboxEvent", "EventbusConsumers-jmapEvent"));
    }

    @Test
    public void deserializeMultipleEventShouldWork(GuiceJamesServer jamesServer) {
        RabbitMQAndRedisEventBus redisEventBus = jamesServer.getProbe(RabbitMQAndRedisEventBusProbe.class)
            .getRabbitMQAndRedisEventBus();

        String multipleEvents = """
            [
                {
                    "Added": {
                        "path": {
                            "namespace": "#private",
                            "user": "xxx@test.org",
                            "name": "Trash"
                        },
                        "eventId": "6e255353-1569-4bb2-b7c3-f2619233050d",
                        "mailboxId": "9d7f7a20-9116-11ef-a47e-a7a56b359103",
                        "added": {
                            "1649": {
                                "uid": 1649,
                                "modSeq": 3258,
                                "flags": {
                                    "systemFlags": ["Seen"],
                                    "userFlags": []
                                },
                                "size": 20764,
                                "internalDate": "2025-03-15T21:36:43.555Z",
                                "saveDate": "2025-03-16T04:02:11.284Z",
                                "messageId": "96ad6550-01e5-11f0-a0b0-7b66f571da65",
                                "threadId": "96ad6550-01e5-11f0-a0b0-7b66f571da65"
                            }
                        },
                        "movedFromMailboxId": "9d745690-9116-11ef-a47e-a7a56b359103",
                        "sessionId": 7074375128256421778,
                        "isDelivery": false,
                        "isAppended": false,
                        "user": "xxx@test.org"
                    }
                },
                {
                    "Expunged": {
                        "path": {
                            "namespace": "#private",
                            "user": "xxx@test.org",
                            "name": "INBOX"
                        },
                        "movedToMailboxId": "9d7f7a20-9116-11ef-a47e-a7a56b359103",
                        "eventId": "da1a0cef-8b75-490a-ae5e-8c2e77a280c2",
                        "mailboxId": "9d745690-9116-11ef-a47e-a7a56b359103",
                        "expunged": {
                            "3708": {
                                "uid": 3708,
                                "modSeq": 19989,
                                "flags": {
                                    "systemFlags": ["Seen"],
                                    "userFlags": []
                                },
                                "size": 20764,
                                "internalDate": "2025-03-15T21:36:43.555Z",
                                "saveDate": "2025-03-15T21:36:43.574Z",
                                "messageId": "96ad6550-01e5-11f0-a0b0-7b66f571da65",
                                "threadId": "96ad6550-01e5-11f0-a0b0-7b66f571da65"
                            }
                        },
                        "sessionId": 7074375128256421778,
                        "user": "xxx@test.org"
                    }
                },
                {
                    "MessageMoveEvent": {
                        "targetMailboxIds": ["9d7f7a20-9116-11ef-a47e-a7a56b359103"],
                        "messageIds": ["96ad6550-01e5-11f0-a0b0-7b66f571da65"],
                        "eventId": "5cffc883-f835-462c-a1c7-cc2c1d884a40",
                        "previousMailboxIds": ["9d745690-9116-11ef-a47e-a7a56b359103"],
                        "user": "xxx@test.org"
                    }
                }
            ]
            """;

        assertThat(redisEventBus.getKeyRegistrationHandler().toEvents(multipleEvents))
            .hasSize(3)
            .satisfiesExactlyInAnyOrder(
                event -> assertThat(event).isInstanceOf(MailboxEvents.Added.class),
                event -> assertThat(event).isInstanceOf(MailboxEvents.Expunged.class),
                event -> assertThat(event).isInstanceOf(MessageMoveEvent.class));
    }

}