package com.linagora.tmail.james;

import static org.apache.james.PostgresJamesConfiguration.EventBusImpl.RABBITMQ;

import org.apache.james.ClockExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.webadmin.RandomPortSupplier;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.PostgresTmailConfiguration;
import com.linagora.tmail.james.app.PostgresTmailServer;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.TeamMailboxesQuotaExtensionsContract;
import com.linagora.tmail.james.common.probe.JmapGuiceContactAutocompleteProbe;
import com.linagora.tmail.james.common.probe.JmapSettingsProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import com.linagora.tmail.team.TeamMailboxProbe;

public class PostgresTeamMailboxesQuotaExtensionsTest extends TeamMailboxesQuotaExtensionsContract {

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<PostgresTmailConfiguration>(tmpDir ->
        PostgresTmailConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .postgres()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .searchConfiguration(SearchConfiguration.openSearch())
            .eventBusImpl(RABBITMQ)
            .build())
        .server(configuration -> PostgresTmailServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(TeamMailboxProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(JmapGuiceContactAutocompleteProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(JmapSettingsProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(JmapSettingsProbe.class))
            .overrideWith(new DelegationProbeModule())
            .overrideWith(Modules.combine(binder -> binder.bind(WebAdminConfiguration.class).toInstance(WebAdminConfiguration.builder()
                    .port(new RandomPortSupplier())
                    .enabled()
                    .host("127.0.0.1")
                    .build()),
                binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                    .addBinding().to(TeamMailboxProbe.class))))
        .extension(PostgresExtension.empty())
        .extension(new RabbitMQExtension())
        .extension(new DockerOpenSearchExtension())
        .extension(new ClockExtension())
        .build();


    @Test
    @Override
    @Disabled("Unstable test. The current quota update procedure does not guarantee concurrency when the previous record is empty.")
    public void teamMailboxesShouldSendOverQuotaEmails(GuiceJamesServer server) {
    }
}
