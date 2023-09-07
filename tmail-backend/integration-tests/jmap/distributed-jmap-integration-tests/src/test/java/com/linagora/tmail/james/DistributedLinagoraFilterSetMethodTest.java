package com.linagora.tmail.james;

import org.apache.james.CassandraExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.LinagoraFilterSetMethodContract;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebasePushClient;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedLinagoraFilterSetMethodTest implements LinagoraFilterSetMethodContract {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig()
                    .disableSingleSave())
            .searchConfiguration(SearchConfiguration.openSearch())
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.ENABLED)
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(binder -> binder.bind(FirebasePushClient.class).toInstance(LinagoraFilterSetMethodContract.firebasePushClient()))
            .overrideWith(new LinagoraTestJMAPServerModule()))
        .build();

    @Override
    public String generateMailboxIdForUser() {
        return CassandraId.of("123e4567-e89b-12d3-a456-426614174000").asUuid().toString();
    }

    @Override
    public String generateMailboxId2ForUser() {
        return CassandraId.of("123e4567-e89b-12d3-a456-426614174001").asUuid().toString();
    }

    @Override
    public String generateAccountIdAsString() {
        return "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6";
    }
}
