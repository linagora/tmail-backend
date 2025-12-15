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

import static com.linagora.tmail.james.app.PostgresTmailConfiguration.EventBusImpl.RABBITMQ_AND_REDIS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerConcreteContract;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.healthcheck.ResultStatus;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import io.restassured.specification.RequestSpecification;

class DistributedPostgresServerWithRedisEventBusTest implements JamesServerConcreteContract {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<PostgresTmailConfiguration>(tmpDir ->
        PostgresTmailConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .postgres()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .enableSingleSave())
            .searchConfiguration(SearchConfiguration.openSearch())
            .eventBusImpl(RABBITMQ_AND_REDIS)
            .build())
        .server(configuration -> PostgresTmailServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(RabbitMQAndRedisEventBusProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class)))
        .extension(new RabbitMQExtension())
        .extension(PostgresExtension.empty())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new DockerOpenSearchExtension())
        .extension(new RedisExtension())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    private RequestSpecification webAdminApi;

    @BeforeEach
    void setUp(GuiceJamesServer guiceJamesServer) {
        this.webAdminApi = WebAdminUtils.spec(guiceJamesServer.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());
    }

    @Test
    public void rabbitEventBusConsumerHealthCheckShouldWork() {
        webAdminApi.when()
            .get("/healthcheck")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", equalTo(ResultStatus.HEALTHY.getValue()))
            .body("checks.componentName", hasItems("EventbusConsumers-mailboxEvent", "EventbusConsumers-jmapEvent",
                "EventbusConsumers-contentDeletionEvent"));
    }

}
