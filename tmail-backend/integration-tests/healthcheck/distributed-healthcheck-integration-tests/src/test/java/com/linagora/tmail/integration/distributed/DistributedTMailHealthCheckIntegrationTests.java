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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.rabbitmq.MonitoredDeadLetterQueue;
import org.apache.james.backends.rabbitmq.MonitoredRabbitMQConsumers;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.healthcheck.ResultStatus;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.rate.limiter.redis.RedisRateLimiterModule;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.integration.TMailHealthCheckIntegrationTests;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedSaaSModule;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.jmap.settings.TWPSettingsModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import com.linagora.tmail.rspamd.RspamdExtensionModule;

import io.restassured.RestAssured;

public class DistributedTMailHealthCheckIntegrationTests extends TMailHealthCheckIntegrationTests {
    public static class MonitoredRabbitMQProbe implements GuiceProbe {
        private final Set<MonitoredRabbitMQConsumers> consumers;
        private final Set<MonitoredDeadLetterQueue> deadLetterQueues;

        @Inject
        public MonitoredRabbitMQProbe(Set<MonitoredRabbitMQConsumers> consumers, Set<MonitoredDeadLetterQueue> deadLetterQueues) {
            this.consumers = consumers;
            this.deadLetterQueues = deadLetterQueues;
        }

        List<String> consumerNames() {
            return consumers.stream()
                .map(MonitoredRabbitMQConsumers::name)
                .toList();
        }

        List<String> deadLetterQueues() {
            return deadLetterQueues.stream()
                .map(MonitoredDeadLetterQueue::queue)
                .toList();
        }
    }

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
            .twpSettingsModuleChooserConfiguration(new TWPSettingsModuleChooserConfiguration(true))
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new RspamdExtensionModule())
        .extension(new RedisExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new RedisRateLimiterModule())
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new DistributedSaaSModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding().to(MonitoredRabbitMQProbe.class)))
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @Test
    void combineImapAndCassandraHealthCheckShouldWork(GuiceJamesServer jamesServer) {
        WebAdminGuiceProbe probe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(probe.getWebAdminPort()).build();

        List<String> listComponentNames =
            given()
                .queryParam("check", "IMAPHealthCheck", "Cassandra backend")
            .when()
                .get("/healthcheck")
            .then()
                .statusCode(HttpStatus.OK_200)
                .extract()
                .body()
                .jsonPath()
                .getList("checks.componentName", String.class);

        assertThat(listComponentNames).containsExactlyInAnyOrder("IMAPHealthCheck", "Cassandra backend");
    }

    @Test
    void everyRabbitMQConsumerShouldBeMonitored(GuiceJamesServer jamesServer) {
        assertThat(jamesServer.getProbe(MonitoredRabbitMQProbe.class).consumerNames())
            .containsExactlyInAnyOrder("mailboxEvent event bus", "jmapEvent event bus", "contentDeletionEvent event bus",
                "task manager", "mail queues",
                "TWP settings", "SaaS subscription", "SaaS domain subscription", "TWP user deletion");
    }

    @Test
    void everyRabbitMQDeadLetterQueueShouldBeMonitored(GuiceJamesServer jamesServer) {
        assertThat(jamesServer.getProbe(MonitoredRabbitMQProbe.class).deadLetterQueues())
            .containsExactlyInAnyOrder("mailboxEvent-dead-letter-queue", "jmapEvent-dead-letter-queue", "contentDeletionEvent-dead-letter-queue",
                "JamesMailQueue-dead-letter-queue-spool",
                "tmail-settings-dead-letter", "tmail-saas-subscription-dead-letter", "tmail-saas-domain-subscription-dead-letter",
                "tmail-user-deletion-dead-letter");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RabbitMQConsumers", "RabbitMQDeadLetterQueues"})
    void rabbitMQHealthChecksShouldBeHealthy(String componentName, GuiceJamesServer jamesServer) {
        WebAdminGuiceProbe probe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(probe.getWebAdminPort()).build();

        await().atMost(30, SECONDS)
            .untilAsserted(() ->
                given()
                .when()
                    .get("/healthcheck/checks/" + componentName)
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .body("status", equalTo(ResultStatus.HEALTHY.getValue())));
    }
}
