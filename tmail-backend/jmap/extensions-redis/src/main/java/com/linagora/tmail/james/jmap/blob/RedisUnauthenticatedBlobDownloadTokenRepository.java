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

package com.linagora.tmail.james.jmap.blob;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.jmap.api.model.AccountId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.hash.Hashing;
import com.linagora.tmail.james.jmap.UnauthenticatedBlobAccessConfiguration;

import reactor.core.publisher.Mono;

public class RedisUnauthenticatedBlobDownloadTokenRepository implements UnauthenticatedBlobDownloadTokenRepository {
    public static String resolveKey(AccountId accountId, BlobId blobId) {
        return KEY_PREFIX + hash(accountId.getIdentifier()) + "_" + hash(blobId.asString());
    }

    public static String hashToken(UnauthenticatedBlobDownloadToken token) {
        return hash(token.value().toString());
    }

    private static String hash(String value) {
        return Hashing.sha512().hashString(value, StandardCharsets.UTF_8).toString();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisUnauthenticatedBlobDownloadTokenRepository.class);
    private static final String KEY_PREFIX = "tmail_unauthenticated_blob_access_blob_";

    private final RedisUnauthenticatedBlobDownloadTokenRepositoryCommands redisCommands;
    private final Duration tokenTtl;

    @Inject
    public RedisUnauthenticatedBlobDownloadTokenRepository(RedisUnauthenticatedBlobDownloadTokenRepositoryCommands redisCommands,
                                                           UnauthenticatedBlobAccessConfiguration configuration) {
        this.redisCommands = redisCommands;
        this.tokenTtl = configuration.tokenTtl();
    }

    @Override
    public Mono<UnauthenticatedBlobDownloadToken> generate(AccountId accountId, BlobId blobId) {
        return Mono.fromCallable(() -> new UnauthenticatedBlobDownloadToken(UUID.randomUUID()))
            .flatMap(token -> redisCommands.set(resolveKey(accountId, blobId), hashToken(token), tokenTtl)
                .thenReturn(token));
    }

    @Override
    public Mono<Boolean> check(AccountId accountId, BlobId blobId, UnauthenticatedBlobDownloadToken token) {
        return redisCommands.get(resolveKey(accountId, blobId))
            .map(storedTokenHash -> storedTokenHash
                .map(hashToken(token)::equals)
                .orElse(false))
            .onErrorResume(error -> {
                LOGGER.warn("Failed to validate unauthenticated blob download token for accountId={} blobId={}", accountId.getIdentifier(), blobId.asString(), error);
                return Mono.just(false);
            });
    }
}
