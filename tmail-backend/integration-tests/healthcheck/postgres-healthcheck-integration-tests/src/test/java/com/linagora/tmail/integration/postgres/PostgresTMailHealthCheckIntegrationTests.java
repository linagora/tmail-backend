package com.linagora.tmail.integration.postgres;

import static com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration.BlobStoreImplName.S3;
import static org.apache.james.PostgresJamesConfiguration.EventBusImpl.RABBITMQ;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.integration.TMailHealthCheckIntegrationTests;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.PostgresTmailConfiguration;
import com.linagora.tmail.james.app.PostgresTmailServer;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import com.linagora.tmail.rspamd.RspamdExtensionModule;

public class PostgresTMailHealthCheckIntegrationTests extends TMailHealthCheckIntegrationTests {

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<PostgresTmailConfiguration>(tmpDir ->
        PostgresTmailConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .implementation(S3)
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .searchConfiguration(SearchConfiguration.openSearch())
            .mailbox(new MailboxConfiguration(false))
            .eventBusImpl(RABBITMQ)
            .build())
        .server(configuration -> PostgresTmailServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class)))
        .extension(PostgresExtension.empty())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new DockerOpenSearchExtension())
        .extension(new RspamdExtensionModule())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @Disabled("We need a TaskExecutionDetailsProjection for PostgresTmailServer")
    @Test
    void tmailHealthCheckShouldBeWellBinded(GuiceJamesServer jamesServer) {

    }

    @Disabled("We need a TaskExecutionDetailsProjection for PostgresTmailServer")
    @Test
    void tasksHealthCheckShouldReturnHealthyByDefault(GuiceJamesServer jamesServer) {

    }
}
