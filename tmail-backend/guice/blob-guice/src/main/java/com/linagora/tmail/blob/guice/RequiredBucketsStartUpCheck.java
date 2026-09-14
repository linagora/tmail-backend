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

package com.linagora.tmail.blob.guice;

import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.lifecycle.api.StartUpCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.blob.bucket.BucketProvisioner;
import com.linagora.tmail.blob.bucket.BucketsStartUpCheckConfiguration;
import com.linagora.tmail.blob.bucket.RequiredBuckets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Makes sure the buckets Twake Mail writes to exist, creating the missing ones, so that a misconfigured object
 * storage fails at boot time with a clear message rather than upon the first write.
 *
 * @see BucketsStartUpCheckConfiguration to turn it off
 */
public class RequiredBucketsStartUpCheck implements StartUpCheck {
    public static final String CHECK_NAME = "required-buckets";

    private static final Logger LOGGER = LoggerFactory.getLogger(RequiredBucketsStartUpCheck.class);
    private static final int CONCURRENCY = 4;

    private final BucketsStartUpCheckConfiguration configuration;
    private final BucketProvisioner bucketProvisioner;
    private final Set<RequiredBuckets> requiredBuckets;

    @Inject
    public RequiredBucketsStartUpCheck(BucketsStartUpCheckConfiguration configuration, BucketProvisioner bucketProvisioner,
                                       Set<RequiredBuckets> requiredBuckets) {
        this.configuration = configuration;
        this.bucketProvisioner = bucketProvisioner;
        this.requiredBuckets = requiredBuckets;
    }

    @Override
    public CheckResult check() {
        if (!configuration.enabled()) {
            LOGGER.warn("Required buckets are not checked at startup: they will be created upon the first write");
            return CheckResult.builder()
                .checkName(CHECK_NAME)
                .resultType(ResultType.GOOD)
                .description("Disabled")
                .build();
        }

        List<String> failures = Flux.fromIterable(requiredBuckets)
            .flatMapIterable(RequiredBuckets::requiredBuckets)
            .distinct()
            .flatMap(bucketName -> bucketProvisioner.ensureExists(bucketName)
                .then(Mono.<String>empty())
                .onErrorResume(e -> {
                    LOGGER.error("Required bucket '{}' is not available", bucketName.asString(), e);
                    return Mono.just(e.getMessage());
                }), CONCURRENCY)
            .collectList()
            .block();

        if (failures.isEmpty()) {
            return CheckResult.builder()
                .checkName(CHECK_NAME)
                .resultType(ResultType.GOOD)
                .build();
        }
        return CheckResult.builder()
            .checkName(CHECK_NAME)
            .resultType(ResultType.BAD)
            .description("Required buckets are not available: " + String.join(" ; ", failures))
            .build();
    }

    @Override
    public String checkName() {
        return CHECK_NAME;
    }
}
