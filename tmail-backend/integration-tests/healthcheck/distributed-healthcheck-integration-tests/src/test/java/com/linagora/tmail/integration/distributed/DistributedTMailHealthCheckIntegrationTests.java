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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.healthcheck.ResultStatus;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.rate.limiter.redis.RedisRateLimiterModule;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.integration.TMailHealthCheckIntegrationTests;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.jmap.settings.TWPSettingsModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import com.linagora.tmail.rspamd.RspamdExtensionModule;

import io.restassured.RestAssured;

public class DistributedTMailHealthCheckIntegrationTests extends TMailHealthCheckIntegrationTests {
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
            .overrideWith(new LinagoraTestJMAPServerModule()))
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
    void twpDeadLetterQueueShouldBeHealthyWhenTWPSettingEnabled(GuiceJamesServer jamesServer) {
        WebAdminGuiceProbe probe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(probe.getWebAdminPort()).build();

        given()
            .queryParam("check", "TWPSettingsDeadLetterQueueHealthCheck")
        .when()
            .get("/healthcheck")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", equalTo(ResultStatus.HEALTHY.getValue()))
            .body("checks.componentName", hasItems("TWPSettingsDeadLetterQueueHealthCheck"));
    }
}
