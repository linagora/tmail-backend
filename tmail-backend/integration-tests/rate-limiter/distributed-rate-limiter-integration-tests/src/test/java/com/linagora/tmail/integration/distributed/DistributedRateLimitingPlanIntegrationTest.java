package com.linagora.tmail.integration.distributed;

import org.apache.james.CassandraExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.rate.limiter.redis.RedisRateLimiterModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.integration.RateLimitingPlanIntegrationContract;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedRateLimitingPlanIntegrationTest implements RateLimitingPlanIntegrationContract {
    private static final MailRepositoryUrl ERROR_REPOSITORY = MailRepositoryUrl.from("cassandra://var/mail/error/");

    @Override
    public MailRepositoryUrl getErrorRepository() {
        return ERROR_REPOSITORY;
    }

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
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new RedisExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new RedisRateLimiterModule())
            .overrideWith(new LinagoraTestJMAPServerModule()))
        .build();
}
