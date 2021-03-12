package com.linagora.openpaas.james;

import static org.apache.james.jmap.JMAPTestingConstants.BOB;

import org.apache.james.CassandraExtension;
import org.apache.james.DockerElasticSearchExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.openpaas.james.app.BlobStoreConfiguration;
import com.linagora.openpaas.james.app.DistributedJamesConfiguration;
import com.linagora.openpaas.james.app.DistributedServer;
import com.linagora.openpaas.james.common.LinagoraFilterGetMethodContract;
import com.linagora.openpaas.james.common.module.JmapGuiceCustomModule;

public class DistributedLinagoraFilterGetMethodTest implements LinagoraFilterGetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
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
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(new JmapGuiceCustomModule()))
        .build();

    @Override
    public String generateMailboxIdForUser() {
        return CassandraId.of("123e4567-e89b-12d3-a456-426614174000").asUuid().toString();
    }

    @Override
    public Username generateUsername() {
        return BOB;
    }

    @Override
    public String generateAccountIdAsString() {
        return "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6";
    }

}
