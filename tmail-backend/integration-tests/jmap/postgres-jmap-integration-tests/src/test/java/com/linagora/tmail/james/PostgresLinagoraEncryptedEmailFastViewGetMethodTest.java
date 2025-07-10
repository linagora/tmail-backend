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

package com.linagora.tmail.james;

import static org.apache.james.PostgresJamesConfiguration.EventBusImpl.IN_MEMORY;

import java.util.function.Supplier;

import org.apache.james.ClockExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.encrypted.postgres.PostgresEncryptedEmailContentStoreModule;
import com.linagora.tmail.encrypted.postgres.PostgresKeystoreModule;
import com.linagora.tmail.james.app.PostgresTmailConfiguration;
import com.linagora.tmail.james.app.PostgresTmailServer;
import com.linagora.tmail.james.common.LinagoraEncryptedEmailFastViewGetMethodContract;
import com.linagora.tmail.james.common.probe.JmapGuiceEncryptedEmailContentStoreProbe;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.method.EncryptedEmailDetailedViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailFastViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreSetMethodModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import com.linagora.tmail.team.TeamMailboxProbe;

public class PostgresLinagoraEncryptedEmailFastViewGetMethodTest implements LinagoraEncryptedEmailFastViewGetMethodContract {

    public static final Supplier<JamesServerExtension> ENCRYPTED_JAMES_SERVER = () -> new JamesServerBuilder<PostgresTmailConfiguration>(tmpDir ->
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
            .mailbox(new MailboxConfiguration(true))
            .eventBusImpl(IN_MEMORY)
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .build())
        .server(configuration -> PostgresTmailServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(TeamMailboxProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(JmapGuiceEncryptedEmailContentStoreProbe.class))
            .overrideWith(new DelegationProbeModule())
            .overrideWith(new PostgresKeystoreModule(), new KeystoreSetMethodModule(), new KeystoreGetMethodModule(), new EncryptedEmailDetailedViewGetMethodModule(), new EncryptedEmailFastViewGetMethodModule(), new PostgresEncryptedEmailContentStoreModule()))
        .extension(PostgresExtension.empty())
        .extension(new ClockExtension())
        .build();

    @RegisterExtension
    static JamesServerExtension testExtension = ENCRYPTED_JAMES_SERVER.get();

    @Override
    public MessageId randomMessageId() {
        return TmailJmapBase.MESSAGE_ID_FACTORY.generate();
    }
}