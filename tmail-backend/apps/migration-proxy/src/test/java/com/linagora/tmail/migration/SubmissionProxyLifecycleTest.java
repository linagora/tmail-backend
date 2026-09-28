/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/


package com.linagora.tmail.migration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.net.ConnectException;
import java.net.Socket;
import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.server.core.configuration.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.linagora.tmail.migration.postgres.MigratedUsersDataDefinition;

class SubmissionProxyLifecycleTest {
    @RegisterExtension
    static PostgresExtension postgresExtension =
        PostgresExtension.withoutRowLevelSecurity(MigratedUsersDataDefinition.MODULE);

    @TempDir
    static File workingDirectory;

    @AfterEach
    void tearDown() {
        List.of("enabled", "old.host", "new.host")
            .forEach(key -> System.clearProperty("migration.submission." + key));
    }

    @Test
    void stoppingTheProxyShouldReleaseTheSubmissionListeners() throws Exception {
        System.setProperty("migration.submission.enabled", "true");
        System.setProperty("migration.submission.old.host", "127.0.0.1");
        System.setProperty("migration.submission.new.host", "127.0.0.1");
        GuiceJamesServer proxy = MigrationProxyServer.createServer(Configuration.builder()
                .workingDirectory(workingDirectory)
                .configurationFromClasspath()
                .build())
            .overrideWith(postgresExtension.getModule(), MigrationProxyImapProbe.MODULE, SubmissionProxyProbe.MODULE);
        proxy.start();
        int port = proxy.getProbe(SubmissionProxyProbe.class).startTlsPort();

        proxy.stop();

        assertThatThrownBy(() -> new Socket("127.0.0.1", port).close())
            .isInstanceOf(ConnectException.class);
    }
}
