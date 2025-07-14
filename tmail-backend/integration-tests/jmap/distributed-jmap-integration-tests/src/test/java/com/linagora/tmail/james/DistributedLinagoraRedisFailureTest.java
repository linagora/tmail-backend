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

package com.linagora.tmail.james;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.requestSpecification;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.jmap.rfc8621.contract.Fixture.authScheme;
import static org.apache.james.jmap.rfc8621.contract.Fixture.baseRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TEN_SECONDS;

import java.time.Duration;

import jakarta.inject.Singleton;

import org.apache.http.HttpStatus;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.redis.DockerRedis;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.events.RedisEventBusConfiguration;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.jmap.rfc8621.contract.Fixture;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedLinagoraRedisFailureTest {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @Nested
    class WhenIgnoreFailure {

        @RegisterExtension
        static JamesServerExtension
            testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
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
            .server(configuration -> DistributedServer.createServer(configuration)
                .overrideWith(new AbstractModule() {
                    @Provides
                    @Singleton
                    RedisEventBusConfiguration redisEventBusConfiguration() {
                        return new RedisEventBusConfiguration(true, Duration.ofSeconds(3));
                    }
                })
                .overrideWith(new LinagoraTestJMAPServerModule()))
            .build();

        @BeforeEach
        void setup(GuiceJamesServer server) throws Exception {
            server.getProbe(DataProbeImpl.class)
                .fluent()
                .addDomain(Fixture.DOMAIN().asString())
                .addUser(Fixture.BOB().asString(), Fixture.BOB_PASSWORD())
                .addUser(Fixture.ANDRE().asString(), Fixture.ANDRE_PASSWORD());

            requestSpecification = baseRequestSpecBuilder(server)
                .setAuth(authScheme(new UserCredential(Fixture.BOB(), Fixture.BOB_PASSWORD())))
                .build();

            server.getProbe(MailboxProbeImpl.class)
                .createMailbox(MailboxPath.inbox(Fixture.BOB()));
            server.getProbe(MailboxProbeImpl.class)
                .createMailbox(MailboxPath.inbox(Fixture.ANDRE()));
        }

        @AfterEach
        void afterEach(DockerRedis dockerRedis) {
            if (dockerRedis.isPaused()) {
                dockerRedis.unPause();
            }
        }

        @Test
        void emailSendShouldSuccessWhenRedisDown(GuiceJamesServer server, DockerRedis dockerRedis) {
            // When redis down
            dockerRedis.pause();

            // Then email send should return created response
            String response = given()
                .body(bobSendsAMailToAndre(server))
            .when()
                .post()
            .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .inPath("methodResponses[0]")
                .isEqualTo("""
                    [
                        "Email/send",
                        {
                            "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                            "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                            "created": {
                                "K87": {
                                    "emailSubmissionId": "${json-unit.ignore}",
                                    "emailId": "${json-unit.ignore}",
                                    "blobId": "${json-unit.ignore}",
                                    "threadId": "${json-unit.ignore}",
                                    "size": "${json-unit.ignore}"
                                }
                            }
                        },
                        "c1"
                    ]
                    """);

            // and email should be sent
            CALMLY_AWAIT.atMost(TEN_SECONDS)
                .untilAsserted(() -> assertThat(server.getProbe(MailboxProbeImpl.class)
                    .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build(),
                        Fixture.ANDRE().asString(), 100))
                    .hasSize(1));
        }

        @Test
        void emailSubmissionShouldSuccessWhenRedisDown(GuiceJamesServer server, DockerRedis dockerRedis) {
            // When redis down
            dockerRedis.pause();

            // Then email send should return created response
            String response = given()
                .body(bobSendAEmailToAndreByEmailSubmissionMethod(server))
            .when()
                .post()
            .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .inPath("methodResponses")
                .isEqualTo("""
                    [
                         [
                             "Email/set",
                             {
                                 "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                                 "oldState": "${json-unit.ignore}",
                                 "newState": "${json-unit.ignore}",
                                 "created": {
                                     "e1526": {
                                         "id": "${json-unit.ignore}",
                                         "blobId": "${json-unit.ignore}",
                                         "threadId": "${json-unit.ignore}",
                                         "size": "${json-unit.ignore}"
                                     }
                                 }
                             },
                             "c1"
                         ],
                         [
                             "EmailSubmission/set",
                             {
                                 "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                                 "newState": "${json-unit.ignore}",
                                 "created": {
                                     "k1490": {
                                         "id": "${json-unit.ignore}",
                                         "sendAt": "${json-unit.ignore}"
                                     }
                                 }
                             },
                             "c2"
                         ]
                     ]
                    """);

            // and email should be sent
            CALMLY_AWAIT.atMost(TEN_SECONDS)
                .untilAsserted(() -> assertThat(server.getProbe(MailboxProbeImpl.class)
                    .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build(),
                        Fixture.ANDRE().asString(), 100))
                    .hasSize(1));
        }
    }

    @Nested
    class WhenNoIgnoreFailure {

        @RegisterExtension
        static JamesServerExtension
            testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
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
                .build())
            .extension(new DockerOpenSearchExtension())
            .extension(new CassandraExtension())
            .extension(new RabbitMQExtension())
            .extension(new RedisExtension())
            .extension(new AwsS3BlobStoreExtension())
            .server(configuration -> DistributedServer.createServer(configuration)
                .overrideWith(new AbstractModule() {
                    @Provides
                    @Singleton
                    RedisEventBusConfiguration redisEventBusConfiguration() {
                        return new RedisEventBusConfiguration(false, Duration.ofSeconds(3));
                    }
                })
                .overrideWith(new LinagoraTestJMAPServerModule()))
            .build();

        @BeforeEach
        void setup(GuiceJamesServer server) throws Exception {
            server.getProbe(DataProbeImpl.class)
                .fluent()
                .addDomain(Fixture.DOMAIN().asString())
                .addUser(Fixture.BOB().asString(), Fixture.BOB_PASSWORD())
                .addUser(Fixture.ANDRE().asString(), Fixture.ANDRE_PASSWORD());

            requestSpecification = baseRequestSpecBuilder(server)
                .setAuth(authScheme(new UserCredential(Fixture.BOB(), Fixture.BOB_PASSWORD())))
                .build();

            server.getProbe(MailboxProbeImpl.class)
                .createMailbox(MailboxPath.inbox(Fixture.BOB()));

            server.getProbe(MailboxProbeImpl.class)
                .createMailbox(MailboxPath.inbox(Fixture.ANDRE()));
        }

        @AfterEach
        void afterEach(DockerRedis dockerRedis) {
            if (dockerRedis.isPaused()) {
                dockerRedis.unPause();
            }
        }

        @Test
        void emailSendShouldFailWhenRedisDown(GuiceJamesServer server, DockerRedis dockerRedis) {
            // When redis down
            dockerRedis.pause();

            // Then email send should fail
            String response = given()
                .body(bobSendsAMailToAndre(server))
            .when()
                .post()
            .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .inPath("methodResponses[0]")
                .isEqualTo("""
                    [
                         "Email/send",
                         {
                             "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                             "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                             "notCreated": {
                                 "K87": {
                                     "type": "serverFail",
                                     "description": "${json-unit.ignore}"
                                 }
                             }
                         },
                         "c1"
                     ]
                    """);
        }

        @Test
        void emailSendShouldWorkNormalWhenRedisWorkBack(GuiceJamesServer server, DockerRedis dockerRedis) {
            // When redis down, then Jmap request should fail
            dockerRedis.pause();

            given()
                .body(bobSendsAMailToAndre(server))
            .when()
                .post()
            .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .body("methodResponses[0][1].notCreated.K87.type", Matchers.is("serverFail"));

            // When redis work back
            dockerRedis.unPause();
            // Then Jmap request should work normal
            given()
                .body(bobSendsAMailToAndre(server))
            .when()
                .post()
            .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .body("methodResponses[0][1].created.K87.emailSubmissionId", Matchers.notNullValue());
        }

        @Test
        void emailSetAndSubmissionShouldFailWhenRedisDown(GuiceJamesServer server, DockerRedis dockerRedis) {
            // When redis down
            dockerRedis.pause();

            // Then email set and submission should fail
            given()
                .body(bobSendAEmailToAndreByEmailSubmissionMethod(server))
            .when()
                .post()
            .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .body("methodResponses[0][1].notCreated.e1526.type", Matchers.is("serverFail"))
                .body("methodResponses[1][1].notCreated.k1490", Matchers.notNullValue());
        }

    }

    public String bobSendsAMailToAndre(GuiceJamesServer server) {
        String accountId = Fixture.ACCOUNT_ID();
        String bobInboxId = server.getProbe(MailboxProbeImpl.class)
            .getMailboxId(MailboxConstants.USER_NAMESPACE, Fixture.BOB().asString(),
                MailboxConstants.INBOX).serialize();
        String bobEmail = Fixture.BOB().asString();
        String andreEmail = Fixture.ANDRE().asString();

        return String.format("""
            {
               "using": [
                  "urn:ietf:params:jmap:core",
                  "urn:ietf:params:jmap:mail",
                  "urn:ietf:params:jmap:submission",
                  "com:linagora:params:jmap:pgp"
               ],
               "methodCalls": [
                  [
                     "Email/send",
                     {
                        "accountId": "%s",
                        "create": {
                           "K87": {
                              "email/create": {
                                 "mailboxIds": {
                                    "%s": true
                                 },
                                 "subject": "World domination",
                                 "htmlBody": [
                                    {
                                       "partId": "a49d",
                                       "type": "text/html"
                                    }
                                 ],
                                 "bodyValues": {
                                    "a49d": {
                                       "value": "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>",
                                       "isTruncated": false,
                                       "isEncodingProblem": false
                                    }
                                 }
                              },
                              "emailSubmission/set": {
                                 "envelope": {
                                    "mailFrom": {
                                       "email": "%s"
                                    },
                                    "rcptTo": [
                                       {
                                          "email": "%s"
                                       }
                                    ]
                                 }
                              }
                           }
                        }
                     },
                     "c1"
                  ]
               ]
            }
            """.stripIndent(), accountId, bobInboxId, bobEmail, andreEmail);
    }


    public String bobSendAEmailToAndreByEmailSubmissionMethod(GuiceJamesServer server) {
        String accountId = Fixture.ACCOUNT_ID();
        String bobInboxId = server.getProbe(MailboxProbeImpl.class)
            .getMailboxId(MailboxConstants.USER_NAMESPACE, Fixture.BOB().asString(),
                MailboxConstants.INBOX).serialize();
        String bobEmail = Fixture.BOB().asString();
        String andreEmail = Fixture.ANDRE().asString();

        return String.format("""
            {
                 "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission" ],
                 "methodCalls": [
                     [
                         "Email/set",
                         {
                             "accountId": "%s",
                             "create": {
                                 "e1526": {
                                     "mailboxIds": { "%s": true },
                                     "subject": "Boredome comes from a boring mind!",
                                     "htmlBody": [ {
                                             "partId": "a49d",
                                             "type": "text/html"
                                         }],
                                     "bodyValues": {
                                         "a49d": {
                                             "value": "<!DOCTYPE html><html><head><title></title></head><body><div>I have the most <b>brilliant</b> plan. Let me tell you all about it. What we do is, we</div></body></html>",
                                             "isTruncated": false,
                                             "isEncodingProblem": false
                                         }
                                     }
                                 }
                             }
                         },
                         "c1"
                     ],
                     [
                         "EmailSubmission/set",
                         {
                             "accountId": "%s",
                             "create": {
                                 "k1490": {
                                     "emailId": "#e1526",
                                     "envelope": {
                                         "mailFrom": { "email": "%s" },
                                         "rcptTo": [ { "email": "%s" } ]
                                     }
                                 }
                             }
                         },
                         "c2"
                     ]
                 ]
             }
            """.stripIndent(), accountId, bobInboxId, accountId, bobEmail, andreEmail);
    }

}
