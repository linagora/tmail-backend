package com.linagora.tmail.james.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.UsersRepositoryModuleChooser;
import com.linagora.tmail.combined.identity.CombinedUsersRepository;
import com.linagora.tmail.combined.identity.LdapExtension;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class CombinedIdentityJamesServerTest {

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.openSearch())
            .usersRepository(UsersRepositoryModuleChooser.Implementation.COMBINED)
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .build())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class)))
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new LdapExtension())
        .extension(new RedisExtension())
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
