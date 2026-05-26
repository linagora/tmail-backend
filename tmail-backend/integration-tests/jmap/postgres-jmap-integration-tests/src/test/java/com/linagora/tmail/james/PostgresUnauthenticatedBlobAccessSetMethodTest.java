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

import static com.linagora.tmail.common.TemporaryTmailServerUtils.BASE_CONFIGURATION_FILE_NAMES;
import static com.linagora.tmail.james.app.PostgresTmailConfiguration.EventBusImpl.IN_MEMORY;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.server.core.configuration.Configuration;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.common.TemporaryTmailServerUtils;
import com.linagora.tmail.james.app.PostgresTmailConfiguration;
import com.linagora.tmail.james.app.PostgresTmailServer;
import com.linagora.tmail.james.common.LabelChangesMethodContract;
import com.linagora.tmail.james.common.UnauthenticatedBlobAccessSetMethodContract;
import com.linagora.tmail.james.common.UnauthenticatedBlobAccessTokenRepositoryProbeModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebasePushClient;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class PostgresUnauthenticatedBlobAccessSetMethodTest implements UnauthenticatedBlobAccessSetMethodContract {
    private static final RedisExtension REDIS_EXTENSION = new RedisExtension();

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<PostgresTmailConfiguration>(tmpDir ->
        PostgresTmailConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationPath(setupConfigurationPath(tmpDir))
            .blobStore(BlobStoreConfiguration.builder()
                .postgres()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .searchConfiguration(SearchConfiguration.scanning())
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.ENABLED)
            .eventBusImpl(IN_MEMORY)
            .build())
        .server(configuration -> PostgresTmailServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> binder.bind(FirebasePushClient.class).toInstance(LabelChangesMethodContract.firebasePushClient()))
            .overrideWith(new DelegationProbeModule())
            .overrideWith(new UnauthenticatedBlobAccessTokenRepositoryProbeModule()))
        .extension(TmailJmapBase.postgresExtension)
        .extension(REDIS_EXTENSION)
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    private static Configuration.ConfigurationPath setupConfigurationPath(File workingDir) {
        TemporaryTmailServerUtils serverUtils = new TemporaryTmailServerUtils(workingDir, ImmutableList.<String>builder()
            .addAll(BASE_CONFIGURATION_FILE_NAMES)
            .add("usersrepository.xml")
            .build());

        try {
            Files.writeString(serverUtils.getConfigFolder().resolve("redis.properties"),
                "redisURL=" + REDIS_EXTENSION.dockerRedis().redisURI() + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return serverUtils.getConfigurationPath();
    }
}
