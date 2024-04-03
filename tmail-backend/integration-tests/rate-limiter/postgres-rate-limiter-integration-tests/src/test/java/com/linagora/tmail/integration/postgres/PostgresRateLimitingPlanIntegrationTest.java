package com.linagora.tmail.integration.postgres;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.PostgresJamesConfiguration;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.rate.limiter.redis.RedisRateLimiterModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.integration.RateLimitingPlanIntegrationContract;
import com.linagora.tmail.james.app.PostgresTmailConfiguration;
import com.linagora.tmail.james.app.PostgresTmailServer;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class PostgresRateLimitingPlanIntegrationTest implements RateLimitingPlanIntegrationContract {
    private static final MailRepositoryUrl ERROR_REPOSITORY = MailRepositoryUrl.from("postgres://var/mail/error/");

    @Override
    public MailRepositoryUrl getErrorRepository() {
        return ERROR_REPOSITORY;
    }

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<PostgresTmailConfiguration>(tmpDir ->
        PostgresTmailConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.scanning())
            .eventBusImpl(PostgresJamesConfiguration.EventBusImpl.RABBITMQ)
            .blobStore(BlobStoreConfiguration.builder()
                .postgres()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .build())
        .extension(PostgresExtension.empty())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .server(configuration -> PostgresTmailServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new RedisRateLimiterModule()))
        .build();
}
