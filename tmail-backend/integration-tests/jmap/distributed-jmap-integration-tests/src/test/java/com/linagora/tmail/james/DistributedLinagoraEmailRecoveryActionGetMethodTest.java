package com.linagora.tmail.james;

import org.apache.james.CassandraExtension;
import org.apache.james.ClockExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.DeletedMessageVaultProbeModule;
import com.linagora.tmail.james.common.EmailRecoveryActionGetMethodContract;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedLinagoraEmailRecoveryActionGetMethodTest implements EmailRecoveryActionGetMethodContract {
    @RegisterExtension
    static JamesServerExtension
        testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .mailbox(new MailboxConfiguration(true))
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new ClockExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new DeletedMessageVaultProbeModule()))
        .build();

    @Override
    public MessageId randomMessageId() {
        return CassandraMessageId.Factory.of(Uuids.timeBased());
    }
}
