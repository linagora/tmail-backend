package com.linagora.openpaas.james;

import static io.restassured.RestAssured.requestSpecification;
import static org.apache.james.jmap.rfc8621.contract.Fixture.authScheme;
import static org.apache.james.jmap.rfc8621.contract.Fixture.baseRequestSpecBuilder;
import static org.apache.james.user.ldap.DockerLdapSingleton.JAMES_USER;
import static org.apache.james.user.ldap.DockerLdapSingleton.PASSWORD;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesConfiguration;
import org.apache.james.DockerElasticSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.core.Username;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.openpaas.james.app.DistributedLdapServer;
import com.linagora.openpaas.james.app.LdapTestExtension;
import com.linagora.openpaas.james.common.LinagoraFilterGetMethodContract;
import com.linagora.openpaas.james.common.module.JmapGuiceCustomModule;

public class DistributedLdapLinagoraFilterGetMethodTest implements LinagoraFilterGetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
        CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .disableCache()
                .deduplication())
            .searchConfiguration(SearchConfiguration.elasticSearch())
            .build())
        .extension(new DockerElasticSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new LdapTestExtension())
        .server(configuration -> DistributedLdapServer.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(new JmapGuiceCustomModule()))
        .build();

    @Override
    @BeforeEach
    public void setUp(GuiceJamesServer server) {
        requestSpecification = baseRequestSpecBuilder(server)
            .setAuth(authScheme(new UserCredential(JAMES_USER, PASSWORD)))
            .build();
    }

    @Override
    public String generateMailboxIdForUser() {
        return CassandraId.of("123e4567-e89b-12d3-a456-426614174000").asUuid().toString();
    }

    @Override
    public Username generateUsername() {
        return JAMES_USER;
    }

    @Override
    public String generateAccountIdAsString() {
        return "0ad82b81befa19e3f8314518723e3f095c9fa77be4c079290617dd2666c7f0ac";
    }


}
