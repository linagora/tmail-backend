package com.linagora.tmail.james.app;

import static org.apache.james.PostgresJamesConfiguration.EventBusImpl.IN_MEMORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.UsersRepositoryModuleChooser;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.combined.identity.CombinedUsersRepository;
import com.linagora.tmail.combined.identity.LdapExtension;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

class CombinedIdentityTmailServerTest {

    static PostgresExtension postgresExtension = PostgresExtension.empty();

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
            .searchConfiguration(SearchConfiguration.scanning())
            .usersRepository(UsersRepositoryModuleChooser.Implementation.COMBINED)
            .mailbox(new MailboxConfiguration(false))
            .eventBusImpl(IN_MEMORY)
            .build())
        .server(configuration -> PostgresTmailServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class)))
        .extension(postgresExtension)
        .extensions(new LdapExtension())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @Test
    void shouldUseCombinedUsersRepositoryWhenSpecified(GuiceJamesServer server) {
        assertThat(server.getProbe(UsersRepositoryClassProbe.class).getUserRepositoryClass())
            .isEqualTo(CombinedUsersRepository.class);
    }

    @Test
    void shouldAllowUserSynchronisation(GuiceJamesServer server) {
        assertThatCode(
            () -> server.getProbe(DataProbeImpl.class)
                .fluent()
                .addDomain("james.org")
                .addUser("james-user@james.org", "123456"))
            .doesNotThrowAnyException();
    }
}
