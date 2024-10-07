package com.linagora.tmail.james;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.jmap.rfc8621.contract.IdentityProbeModule;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.blobguice.BlobStoreConfiguration;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.PublicAssetSetMethodContract;
import com.linagora.tmail.james.common.probe.PublicAssetProbeModule;
import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedPublicAssetSetSetMethodTest implements PublicAssetSetMethodContract {
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
            .eventBusKeysChoice(EventBusKeysChoice.RABBITMQ)
            .searchConfiguration(SearchConfiguration.scanning())
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .build())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new IdentityProbeModule())
            .overrideWith(new DelegationProbeModule())
            .overrideWith(new PublicAssetProbeModule())
            .overrideWith(binder -> binder.bind(JMAPExtensionConfiguration.class)
                .toInstance(PublicAssetSetMethodContract.CONFIGURATION())))
        .build();
}
