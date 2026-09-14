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

package com.linagora.tmail.blob.bucket;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectStoreException;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration;
import org.apache.james.blob.objectstorage.aws.S3ClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3BucketProvisioner implements BucketProvisioner {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3BucketProvisioner.class);
    private static final int NOT_FOUND = 404;

    private final S3ClientFactory clientFactory;
    private final URI endpoint;
    private final Optional<BucketName> namespace;
    private final Optional<String> bucketPrefix;

    public S3BucketProvisioner(S3ClientFactory clientFactory, S3BlobStoreConfiguration configuration) {
        this.clientFactory = clientFactory;
        this.endpoint = configuration.getSpecificAuthConfiguration().getEndpoint();
        this.namespace = configuration.getNamespace();
        this.bucketPrefix = configuration.getBucketPrefix();
    }

    @Override
    public Mono<Void> ensureExists(BucketName bucketName) {
        String physicalBucket = resolve(bucketName).asString();

        return bucketExists(physicalBucket)
            .onErrorMap(e -> new ObjectStoreException("Could not check whether bucket '" + physicalBucket + "' exists on " + endpoint + ": " + describe(e), e))
            .flatMap(exists -> {
                if (exists) {
                    return Mono.empty();
                }
                return createBucket(physicalBucket);
            });
    }

    /**
     * Mirrors James' package private {@code BucketNameResolver}: the namespace - ie the default bucket - is the one
     * bucket not prefixed.
     */
    private BucketName resolve(BucketName bucketName) {
        if (namespace.map(bucketName::equals).orElse(false)) {
            return bucketName;
        }
        return bucketPrefix
            .map(prefix -> BucketName.of(prefix + bucketName.asString()))
            .orElse(bucketName);
    }

    private Mono<Boolean> bucketExists(String physicalBucket) {
        return Mono.fromFuture(() -> clientFactory.get().headBucket(builder -> builder.bucket(physicalBucket)))
            .thenReturn(true)
            .onErrorResume(S3BucketProvisioner::isMissingBucket, e -> Mono.just(false));
    }

    private Mono<Void> createBucket(String physicalBucket) {
        return Mono.fromFuture(() -> clientFactory.get().createBucket(builder -> builder.bucket(physicalBucket)))
            .doOnSuccess(any -> LOGGER.info("Created missing bucket '{}' on {}", physicalBucket, endpoint))
            .then()
            // Another Twake Mail node starting concurrently won the race
            .onErrorResume(e -> unwrap(e) instanceof BucketAlreadyOwnedByYouException, e -> Mono.empty())
            .onErrorMap(e -> new ObjectStoreException("Bucket '" + physicalBucket + "' does not exist on " + endpoint + " and could not be created: " + describe(e), e));
    }

    private static boolean isMissingBucket(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        return cause instanceof NoSuchBucketException
            || (cause instanceof S3Exception s3Exception && s3Exception.statusCode() == NOT_FOUND);
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private static String describe(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        return cause.getClass().getSimpleName() + " " + cause.getMessage();
    }
}
