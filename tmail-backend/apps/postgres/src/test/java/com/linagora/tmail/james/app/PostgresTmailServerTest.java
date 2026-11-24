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

package com.linagora.tmail.james.app;

import static com.linagora.tmail.james.app.PostgresTmailConfiguration.EventBusImpl.IN_MEMORY;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.net.smtp.SMTPClient;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerConcreteContract;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.jmap.JmapJamesServerContract;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

class PostgresTmailServerTest implements JamesServerConcreteContract, JmapJamesServerContract {
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
                .enableSingleSave())
            .searchConfiguration(SearchConfiguration.scanning())
            .eventBusImpl(IN_MEMORY)
            .build())
        .server(configuration -> PostgresTmailServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class)))
        .extension(postgresExtension)
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    private static final String LOCALHOST = "127.0.0.1";
    private static final String RCPT_CHECK_DOMAIN = "rcpt-check.tld";

    @Test
    void smtpShouldRejectUnknownLocalRecipient(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).addDomain(RCPT_CHECK_DOMAIN);
        SMTPClient client = new SMTPClient();
        client.connect(LOCALHOST,server.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());

        try {
            client.helo("external.tld");
            client.setSender("victim@external.tld");
            client.addRecipient("nobody@" + RCPT_CHECK_DOMAIN);

            assertThat(client.getReplyCode()).isEqualTo(550);
        } finally {
            client.disconnect();
        }
    }
}
