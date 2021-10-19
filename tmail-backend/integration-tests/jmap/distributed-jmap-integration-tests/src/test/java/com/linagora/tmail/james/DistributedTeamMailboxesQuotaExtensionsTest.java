package com.linagora.tmail.james;

import org.apache.james.CassandraExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.webadmin.RandomPortSupplier;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerElasticSearchExtension;
import com.linagora.tmail.james.common.TeamMailboxesQuotaExtensionsContract;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedTeamMailboxesQuotaExtensionsTest extends TeamMailboxesQuotaExtensionsContract {
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
            .searchConfiguration(SearchConfiguration.elasticSearch())
            .build())
        .extension(new DockerElasticSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule(),
                binder -> binder.bind(WebAdminConfiguration.class).toInstance(WebAdminConfiguration.builder()
                    .port(new RandomPortSupplier())
                    .enabled()
                    .host("127.0.0.1")
                    .build())))
        .build();
}
