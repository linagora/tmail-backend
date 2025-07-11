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

import static com.linagora.tmail.james.TmailJmapBase.postgresExtension;
import static org.apache.james.PostgresJamesConfiguration.EventBusImpl.IN_MEMORY;

import java.net.URL;
import java.util.Optional;

import org.apache.james.ClockExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.jmap.core.JmapRfc8621Configuration;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.common.OidcAuthenticationContract;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.james.app.PostgresTmailConfiguration;
import com.linagora.tmail.james.app.PostgresTmailServer;
import com.linagora.tmail.james.common.LabelChangesMethodContract;
import com.linagora.tmail.james.common.probe.JmapGuiceContactAutocompleteProbe;
import com.linagora.tmail.james.common.probe.JmapSettingsProbe;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebasePushClient;
import com.linagora.tmail.james.jmap.oidc.OidcTokenCacheModuleChooser;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import com.linagora.tmail.team.TeamMailboxProbe;

public class PostgresOidcAuthenticationTest extends OidcAuthenticationContract {
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
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.ENABLED)
            .eventBusImpl(IN_MEMORY)
            .oidcEnabled(true)
            .oidcTokenStorageChoice(OidcTokenCacheModuleChooser.OidcTokenCacheChoice.REDIS)
            .build())
        .server(configuration -> PostgresTmailServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(TeamMailboxProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(JmapGuiceContactAutocompleteProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(JmapSettingsProbe.class))
            .overrideWith(binder -> binder.bind(FirebasePushClient.class).toInstance(LabelChangesMethodContract.firebasePushClient()))
            .overrideWith(binder -> binder.bind(JmapRfc8621Configuration.class).toInstance(JmapRfc8621Configuration.LOCALHOST_CONFIGURATION()
                    .withAuthenticationStrategies(OIDC_AUTHENTICATION_STRATEGY)))
            .overrideWith(binder -> binder.bind(URL.class).annotatedWith(Names.named("userInfo"))
                .toProvider(OidcAuthenticationContract::getUserInfoTokenEndpoint))
            .overrideWith(binder -> binder.bind(IntrospectionEndpoint.class)
                .toProvider(() -> new IntrospectionEndpoint(getIntrospectTokenEndpoint(), Optional.empty())))
            .overrideWith(new DelegationProbeModule()))
        .extension(postgresExtension)
        .extension(new RedisExtension())
        .extension(new ClockExtension())
        .build();
}
