package com.linagora.tmail.james;

import static com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration.BlobStoreImplName.S3;

import org.apache.james.CassandraExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.LinagoraCalendarEventAcceptMethodContract;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedLinagoraCalendarEventAcceptMethodTest implements LinagoraCalendarEventAcceptMethodContract {

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
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule(), new DelegationProbeModule()))
        .build();

    @Override
    public String randomBlobId() {
        return Uuids.timeBased().toString();
    }
}
