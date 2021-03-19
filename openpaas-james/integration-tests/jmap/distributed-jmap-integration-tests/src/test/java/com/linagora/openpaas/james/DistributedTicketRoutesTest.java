package com.linagora.openpaas.james;

import org.apache.james.CassandraExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.openpaas.james.app.DistributedJamesConfiguration;
import com.linagora.openpaas.james.app.DistributedServer;
import com.linagora.openpaas.james.app.DockerElasticSearchExtension;
import com.linagora.openpaas.james.common.LinagoraEchoMethodContract;
import com.linagora.openpaas.james.common.LinagoraTicketAuthenticationContract;

public class DistributedTicketRoutesTest implements LinagoraTicketAuthenticationContract {
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
            .overrideWith(new TestJMAPServerModule()))
        .build();
}
