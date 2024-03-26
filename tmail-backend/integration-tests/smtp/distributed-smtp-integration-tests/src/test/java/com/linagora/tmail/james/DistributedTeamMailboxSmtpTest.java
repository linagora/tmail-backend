package com.linagora.tmail.james;

import static com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration.BlobStoreImplName.S3;

import org.apache.james.CassandraExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.TeamMailboxSmtpContract;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import com.linagora.tmail.team.TeamMailboxProbe;

@Tag(BasicFeature.TAG)
public class DistributedTeamMailboxSmtpTest extends TeamMailboxSmtpContract {
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
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding().to(TeamMailboxProbe.class)))
        .build();
}
