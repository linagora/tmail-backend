package com.linagora.tmail.james;

import static com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration.BlobStoreImplName.POSTGRES;
import static org.apache.james.PostgresJamesConfiguration.EventBusImpl.IN_MEMORY;

import org.apache.james.ClockExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.james.app.PostgresTmailConfiguration;
import com.linagora.tmail.james.app.PostgresTmailServer;
import com.linagora.tmail.james.common.JmapSettingsSetMethodContract;
import com.linagora.tmail.james.common.LabelChangesMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceLabelModule;
import com.linagora.tmail.james.common.probe.JmapSettingsProbe;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebasePushClient;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class PostgresLinagoraJmapSettingsSetMethodTest implements JmapSettingsSetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<PostgresTmailConfiguration>(tmpDir ->
        PostgresTmailConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .implementation(POSTGRES)
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .searchConfiguration(SearchConfiguration.scanning())
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.ENABLED)
            .mailbox(new MailboxConfiguration(false))
            .eventBusImpl(IN_MEMORY)
            .build())
        .server(configuration -> PostgresTmailServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new DelegationProbeModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(JmapSettingsProbe.class))
            .overrideWith(Modules.combine(new JmapGuiceLabelModule(), binder -> binder.bind(FirebasePushClient.class).toInstance(LabelChangesMethodContract.firebasePushClient()))))
        .extension(PostgresExtension.empty())
        .extension(new ClockExtension())
        .build();
}
