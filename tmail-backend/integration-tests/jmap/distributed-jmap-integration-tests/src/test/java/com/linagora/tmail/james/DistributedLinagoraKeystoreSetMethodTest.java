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

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.encrypted.cassandra.EncryptedEmailContentStoreCassandraModule;
import com.linagora.tmail.encrypted.cassandra.KeystoreCassandraModule;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.LinagoraKeystoreSetMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceKeystoreManagerModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.method.EncryptedEmailDetailedViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailFastViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreSetMethodModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

class DistributedLinagoraKeystoreSetMethodTest implements LinagoraKeystoreSetMethodContract {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .noSecondaryS3BlobStore()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new DelegationProbeModule())
            .overrideWith(new JmapGuiceKeystoreManagerModule())
            .overrideWith(new KeystoreCassandraModule(), new KeystoreSetMethodModule(), new KeystoreGetMethodModule(), new EncryptedEmailContentStoreCassandraModule(), new EncryptedEmailDetailedViewGetMethodModule(), new EncryptedEmailFastViewGetMethodModule()))
        .build();
}
