package com.linagora.tmail.james;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.cassandra.change.CassandraStateFactory;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.blobguice.BlobStoreConfiguration;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.LabelChangesMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceLabelModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebasePushClient;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedLinagoraLabelChangesMethodTest implements LabelChangesMethodContract {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave()
                .noSecondaryS3BlobStoreConfig())
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .searchConfiguration(SearchConfiguration.openSearch())
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.ENABLED)
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new JmapGuiceLabelModule())
                .overrideWith(binder -> binder.bind(FirebasePushClient.class).toInstance(LabelChangesMethodContract.firebasePushClient()))
            .overrideWith(new DelegationProbeModule()))
        .build();

    @Override
    public State.Factory stateFactory() {
        return new CassandraStateFactory();
    }
}
