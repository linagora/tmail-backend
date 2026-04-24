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

import static com.linagora.tmail.blob.guice.BlobStoreModulesChooser.INITIAL_BLOBSTORE_DAO;
import static com.linagora.tmail.blob.guice.BlobStoreModulesChooser.MAYBE_ENCRYPTION_BLOBSTORE;
import static org.apache.james.blob.api.BlobStoreDAO.ContentTransferEncoding.ZSTD;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.zstd.CompressionConfiguration;
import org.apache.james.blob.zstd.ZstdBlobStoreDAO;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.luben.zstd.Zstd;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import reactor.core.publisher.Mono;

class DistributedWithEncryptedAndZstdBlobStoreTest {
    private record BlobSnapshot(byte[] originalPayload, byte[] readPayload, BlobStoreDAO.BytesBlob encryptionLayerBlob,
                                BlobStoreDAO.BytesBlob rawStoredBlob) {
    }

    static class EncryptedZstdBlobStoreProbe implements GuiceProbe {
        private final BlobStore blobStore;
        private final BlobStoreDAO encryptionBlobStoreDAO;
        private final BlobStoreDAO rawBlobStoreDAO;

        @Inject
        public EncryptedZstdBlobStoreProbe(BlobStore blobStore,
                                           @Named(MAYBE_ENCRYPTION_BLOBSTORE) BlobStoreDAO encryptionBlobStoreDAO,
                                           @Named(INITIAL_BLOBSTORE_DAO) BlobStoreDAO rawBlobStoreDAO) {
            this.blobStore = blobStore;
            this.encryptionBlobStoreDAO = encryptionBlobStoreDAO;
            this.rawBlobStoreDAO = rawBlobStoreDAO;
        }

        BlobSnapshot saveAndRead(String payload) {
            byte[] originalPayload = payload.getBytes(StandardCharsets.UTF_8);
            BlobId blobId = Mono.from(blobStore.save(blobStore.getDefaultBucketName(), originalPayload, BlobStore.StoragePolicy.LOW_COST))
                .block();
            byte[] readPayload = Mono.from(blobStore.readBytes(blobStore.getDefaultBucketName(), blobId))
                .block();
            BlobStoreDAO.BytesBlob encryptionLayerBlob = Mono.from(encryptionBlobStoreDAO.readBytes(blobStore.getDefaultBucketName(), blobId))
                .block();
            BlobStoreDAO.BytesBlob rawStoredBlob = Mono.from(rawBlobStoreDAO.readBytes(blobStore.getDefaultBucketName(), blobId))
                .block();

            return new BlobSnapshot(originalPayload, readPayload, encryptionLayerBlob, rawStoredBlob);
        }
    }

    private static final String COMPRESSIBLE_PAYLOAD = "Twake Mail distributed encrypted zstd blob store integration payload.\n".repeat(2048);

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .noSecondaryS3BlobStore()
                .disableCache()
                .passthrough()
                .withCryptoConfig(CryptoConfig.builder()
                    .password("myPass".toCharArray())
                    .salt("73616c7479")
                    .build())
                .compressionConfig(CompressionConfiguration.builder()
                    .enabled(true)
                    .threshold(1)
                    .build())
                .enableSingleSave())
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .searchConfiguration(SearchConfiguration.openSearch())
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding()
                .to(EncryptedZstdBlobStoreProbe.class)))
        .build();

    @Test
    void blobStoreShouldCompressBeforeEncryptingBlobs(GuiceJamesServer server) {
        BlobSnapshot blobSnapshot = server.getProbe(EncryptedZstdBlobStoreProbe.class).saveAndRead(COMPRESSIBLE_PAYLOAD);

        assertSoftly(softly -> {
            // read should round trip to the original saving payload
            softly.assertThat(blobSnapshot.readPayload()).isEqualTo(blobSnapshot.originalPayload());

            // The intermediate encryption layer returns bytes that were zstd-compressed:
            // they differ from the original payload, keep zstd metadata, and round-trip through zstd decompression back to the original payload.
            softly.assertThat(blobSnapshot.encryptionLayerBlob().payload()).isNotEqualTo(blobSnapshot.originalPayload());
            softly.assertThat(blobSnapshot.encryptionLayerBlob().metadata().contentTransferEncoding()).contains(ZSTD);
            softly.assertThat(Zstd.decompress(blobSnapshot.encryptionLayerBlob().payload(), blobSnapshot.originalPayload().length))
                .isEqualTo(blobSnapshot.originalPayload());

            // Raw storage must therefore be the encrypted form of those compressed bytes.
            softly.assertThat(blobSnapshot.rawStoredBlob().payload()).isNotEqualTo(blobSnapshot.originalPayload());
            softly.assertThat(blobSnapshot.rawStoredBlob().payload()).isNotEqualTo(blobSnapshot.encryptionLayerBlob().payload());
            softly.assertThat(blobSnapshot.rawStoredBlob().metadata().contentTransferEncoding()).contains(ZSTD);
            softly.assertThat(blobSnapshot.rawStoredBlob().metadata().get(ZstdBlobStoreDAO.CONTENT_ORIGINAL_SIZE))
                .contains(new BlobStoreDAO.BlobMetadataValue(String.valueOf(blobSnapshot.originalPayload().length)));
        });
    }
}
