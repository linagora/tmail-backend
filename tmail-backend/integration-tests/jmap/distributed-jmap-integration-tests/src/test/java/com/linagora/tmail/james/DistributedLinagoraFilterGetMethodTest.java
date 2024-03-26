package com.linagora.tmail.james;

import static com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration.BlobStoreImplName.S3;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;

import org.apache.james.CassandraExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.LinagoraFilterGetMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceCustomModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedLinagoraFilterGetMethodTest implements LinagoraFilterGetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                    .implementation(S3)
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig()
                    .disableSingleSave())
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .searchConfiguration(SearchConfiguration.openSearch())
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
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
