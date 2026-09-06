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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.GuiceJamesServer;
import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Container;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Singleton;
import org.apache.james.blob.objectstorage.aws.Region;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration;
import org.apache.james.blob.objectstorage.aws.S3ClientFactory;
import org.apache.james.blob.objectstorage.aws.S3RequestOption;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.blob.sharding.BucketSharding;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

/**
 * End to end picture of the physical S3 bucket layout, over the four logical buckets a Twake Mail deployment writes
 * to, with and without applicative sharding.
 *
 * <p>The logical bucket names are fixed here so that the expected physical names can be spelled out. Note that the
 * default bucket - the {@code objectstorage.namespace} - is the one bucket S3 does not prefix, an exemption that only
 * holds as long as its name is left untouched: once sharded, {@code blobs-0} is no longer the namespace and thus does
 * get the prefix.</p>
 */
class DistributedBlobStoreBucketShardingTest {
    private static final int SHARD_COUNT = 4;
    private static final int BLOBS_PER_BUCKET = 16;
    /** {@code objectstorage.bucketPrefix} */
    private static final String BUCKET_PREFIX = "tmail-";

    /** The default bucket, holding mailbox content. Named after {@code objectstorage.namespace}. */
    private static final BucketName DEFAULT_BUCKET = BucketName.of("blobs");
    /** JMAP uploads. */
    private static final BucketName UPLOADS_BUCKET = BucketName.of("jmap-uploads");
    /** Deleted message vault. */
    private static final BucketName VAULT_BUCKET = BucketName.of("tmail-deleted-message-vault");
    /** Mails in transit, when {@code mailprocessing.deduplication.enabled} is false. */
    private static final BucketName MAIL_PROCESSING_BUCKET = BucketName.of("mail-processing");

    private static final List<BucketName> LOGICAL_BUCKETS =
        ImmutableList.of(DEFAULT_BUCKET, UPLOADS_BUCKET, VAULT_BUCKET, MAIL_PROCESSING_BUCKET);

    /**
     * Binds the object storage onto the shared docker S3, with a chosen prefix and default bucket name rather than
     * the random ones {@code AwsS3BlobStoreExtension} uses, so that physical bucket names can be asserted upon.
     */
    static class FixedNamesAwsS3BlobStoreExtension implements GuiceModuleTestExtension {
        @Override
        public void beforeAll(ExtensionContext extensionContext) {
            DockerAwsS3Singleton.singleton.dockerAwsS3();
        }

        @Override
        public Module getModule() {
            AwsS3AuthConfiguration authConfiguration = AwsS3AuthConfiguration.builder()
                .endpoint(DockerAwsS3Singleton.singleton.getEndpoint())
                .accessKeyId(DockerAwsS3Container.ACCESS_KEY_ID)
                .secretKey(DockerAwsS3Container.SECRET_ACCESS_KEY)
                .build();
            S3BlobStoreConfiguration configuration = S3BlobStoreConfiguration.builder()
                .authConfiguration(authConfiguration)
                .region(DockerAwsS3Container.REGION)
                .defaultBucketName(DEFAULT_BUCKET)
                .bucketPrefix(BUCKET_PREFIX)
                .build();

            return binder -> {
                binder.bind(BucketName.class).toInstance(DEFAULT_BUCKET);
                binder.bind(Region.class).toInstance(DockerAwsS3Container.REGION);
                binder.bind(AwsS3AuthConfiguration.class).toInstance(authConfiguration);
                binder.bind(S3BlobStoreConfiguration.class).toInstance(configuration);
                binder.bind(S3RequestOption.class).toInstance(S3RequestOption.DEFAULT);
            };
        }
    }

    public static class BucketLayoutProbe implements GuiceProbe {
        private final BlobStoreDAO blobStoreDAO;
        private final S3ClientFactory s3ClientFactory;

        @Inject
        public BucketLayoutProbe(BlobStoreDAO blobStoreDAO, S3ClientFactory s3ClientFactory) {
            this.blobStoreDAO = blobStoreDAO;
            this.s3ClientFactory = s3ClientFactory;
        }

        /**
         * The docker S3 is shared by the whole test run, so what this server created is the difference between the
         * two listings rather than the whole bucket list.
         */
        List<String> bucketsCreatedByFillingEveryLogicalBucket() {
            List<String> before = physicalBuckets();
            fillEveryLogicalBucket();
            return physicalBuckets().stream()
                .filter(bucket -> !before.contains(bucket))
                .collect(ImmutableList.toImmutableList());
        }

        private void fillEveryLogicalBucket() {
            Flux.fromIterable(LOGICAL_BUCKETS)
                .concatMap(bucketName -> Flux.range(0, BLOBS_PER_BUCKET)
                    .concatMap(i -> blobStoreDAO.save(bucketName,
                        new PlainBlobId(bucketName.asString() + "-blob-" + i),
                        BlobStoreDAO.BytesBlob.of("payload " + i))))
                .then()
                .block();
        }

        List<String> physicalBuckets() {
            return Mono.fromFuture(() -> s3ClientFactory.get().listBuckets())
                .flatMapIterable(ListBucketsResponse::buckets)
                .map(Bucket::name)
                .collectList()
                .block();
        }
    }

    private static JamesServerExtension serverExtension(Optional<BucketSharding> bucketSharding) {
        return new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
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
                .searchConfiguration(SearchConfiguration.openSearch())
                .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                .build())
            .extension(new DockerOpenSearchExtension())
            .extension(new CassandraExtension())
            .extension(new RabbitMQExtension())
            .extension(new RedisExtension())
            .extension(new FixedNamesAwsS3BlobStoreExtension())
            .server(configuration -> DistributedServer.createServer(configuration)
                .overrideWith(new LinagoraTestJMAPServerModule())
                .overrideWith(binder -> binder.bind(new TypeLiteral<Optional<BucketSharding>>() {})
                    .toInstance(bucketSharding))
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                    .addBinding()
                    .to(BucketLayoutProbe.class)))
            .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
            .build();
    }

    @Nested
    class WithFourShards {
        @RegisterExtension
        static JamesServerExtension testExtension = serverExtension(Optional.of(new BucketSharding(SHARD_COUNT)));

        @Test
        void everyLogicalBucketShouldBeSplitInFourPhysicalBuckets(GuiceJamesServer server) {
            assertThat(server.getProbe(BucketLayoutProbe.class).bucketsCreatedByFillingEveryLogicalBucket())
                .containsExactlyInAnyOrder(
                    "tmail-blobs-0", "tmail-blobs-1", "tmail-blobs-2", "tmail-blobs-3",
                    "tmail-jmap-uploads-0", "tmail-jmap-uploads-1", "tmail-jmap-uploads-2", "tmail-jmap-uploads-3",
                    "tmail-tmail-deleted-message-vault-0", "tmail-tmail-deleted-message-vault-1",
                    "tmail-tmail-deleted-message-vault-2", "tmail-tmail-deleted-message-vault-3",
                    "tmail-mail-processing-0", "tmail-mail-processing-1",
                    "tmail-mail-processing-2", "tmail-mail-processing-3");
        }
    }

    @Nested
    class WithoutSharding {
        @RegisterExtension
        static JamesServerExtension testExtension = serverExtension(Optional.empty());

        @Test
        void everyLogicalBucketShouldKeepItsUnshardedPhysicalName(GuiceJamesServer server) {
            assertThat(server.getProbe(BucketLayoutProbe.class).bucketsCreatedByFillingEveryLogicalBucket())
                .containsExactlyInAnyOrder(
                    // the default bucket is the namespace, and as such is the one bucket left unprefixed
                    "blobs",
                    "tmail-jmap-uploads",
                    "tmail-tmail-deleted-message-vault",
                    "tmail-mail-processing");
        }
    }
}
