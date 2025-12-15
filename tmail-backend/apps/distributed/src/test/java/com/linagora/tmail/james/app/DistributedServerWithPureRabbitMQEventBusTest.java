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
import org.apache.james.core.healthcheck.ResultStatus;
import org.apache.james.jmap.JmapJamesServerContract;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
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

class DistributedServerWithPureRabbitMQEventBusTest implements JamesServerConcreteContract, JmapJamesServerContract {
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
            .eventBusKeysChoice(EventBusKeysChoice.RABBITMQ)
            .build())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class)))
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
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
    public void healthcheckShouldBeHealthy(GuiceJamesServer jamesServer) {
        WebAdminGuiceProbe probe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(probe.getWebAdminPort()).build();

        given()
        .when()
            .get("/healthcheck")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", equalTo(ResultStatus.HEALTHY.getValue()))
            .body("checks.componentName", hasItems("EventbusConsumers-mailboxEvent", "EventbusConsumers-jmapEvent",
                "EventbusConsumers-contentDeletionEvent"));
    }
}