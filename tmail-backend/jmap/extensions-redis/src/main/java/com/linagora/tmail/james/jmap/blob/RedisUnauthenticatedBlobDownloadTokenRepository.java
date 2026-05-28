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
import java.util.Base64;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.hash.Hashing;
import com.linagora.tmail.james.jmap.UnauthenticatedBlobAccessConfiguration;

import reactor.core.publisher.Mono;

public class RedisUnauthenticatedBlobDownloadTokenRepository implements UnauthenticatedBlobDownloadTokenRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisUnauthenticatedBlobDownloadTokenRepository.class);
    private static final String KEY_PREFIX = "tmail_unauthenticated_blob_access_blob_";
    private static final String VALUE_DELIMITER = ":";

    public static String resolveKey(AccountId accountId, BlobId blobId) {
        return KEY_PREFIX + hash(accountId.getIdentifier()) + "_" + hash(blobId.asString());
    }

    public static String hashToken(UnauthenticatedBlobDownloadToken token) {
        return hash(token.value().toString());
    }

    static String encodeUsername(Username username) {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(username.asString().getBytes(StandardCharsets.UTF_8));
    }

    static String formatValue(UnauthenticatedBlobDownloadToken token, Username username) {
        return hashToken(token) + VALUE_DELIMITER + encodeUsername(username);
    }

    private static String hash(String value) {
        return Hashing.sha512().hashString(value, StandardCharsets.UTF_8).toString();
    }

    private static Optional<Username> parseUsername(String value) {
        String[] parts = value.split(VALUE_DELIMITER, -1);
        if (parts.length != 2) {
            LOGGER.warn("Malformed unauthenticated blob download token stored value: {}", value);
            return Optional.empty();
        }

        String username = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return Optional.of(Username.of(username));
    }

    private final RedisUnauthenticatedBlobDownloadTokenRepositoryCommands redisCommands;
    private final Duration tokenTtl;

    @Inject
    public RedisUnauthenticatedBlobDownloadTokenRepository(RedisUnauthenticatedBlobDownloadTokenRepositoryCommands redisCommands,
                                                           UnauthenticatedBlobAccessConfiguration configuration) {
        this.redisCommands = redisCommands;
        this.tokenTtl = configuration.tokenTtl();
    }

    @Override
    public Mono<UnauthenticatedBlobDownloadToken> generate(AccountId accountId, BlobId blobId, Username username) {
        return Mono.fromCallable(UnauthenticatedBlobDownloadToken::generate)
            .flatMap(token -> redisCommands.set(resolveKey(accountId, blobId), formatValue(token, username), tokenTtl)
                .thenReturn(token));
    }

    @Override
    public Mono<Optional<Username>> check(AccountId accountId, BlobId blobId, UnauthenticatedBlobDownloadToken token) {
        return redisCommands.get(resolveKey(accountId, blobId))
            .map(storedValue -> storedValue
                .filter(value -> value.startsWith(hashToken(token) + VALUE_DELIMITER))
                .flatMap(RedisUnauthenticatedBlobDownloadTokenRepository::parseUsername))
            .onErrorResume(error -> {
                LOGGER.warn("Failed to validate unauthenticated blob download token for accountId={} blobId={}", accountId.getIdentifier(), blobId.asString(), error);
                return Mono.just(Optional.empty());
            });
    }
}
