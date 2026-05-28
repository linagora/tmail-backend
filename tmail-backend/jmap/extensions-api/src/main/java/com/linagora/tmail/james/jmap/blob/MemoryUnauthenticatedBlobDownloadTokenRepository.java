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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.james.blob.api.BlobId;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class MemoryUnauthenticatedBlobDownloadTokenRepository implements UnauthenticatedBlobDownloadTokenRepository {
    private record TokenKey(AccountId accountId, BlobId blobId) {
    }

    private record StoredToken(UnauthenticatedBlobDownloadToken token, Username username, Instant expiresAt) {
    }

    private final ConcurrentMap<TokenKey, StoredToken> store;
    private final Clock clock;
    private final Duration tokenTtl;

    public MemoryUnauthenticatedBlobDownloadTokenRepository(Clock clock, Duration tokenTtl) {
        Preconditions.checkArgument(!tokenTtl.isZero() && !tokenTtl.isNegative(), "tokenTtl must be positive");

        this.store = new ConcurrentHashMap<>();
        this.clock = clock;
        this.tokenTtl = tokenTtl;
    }

    @Override
    public Mono<UnauthenticatedBlobDownloadToken> generate(AccountId accountId, BlobId blobId, Username username) {
        return Mono.fromCallable(() -> {
            UnauthenticatedBlobDownloadToken token = UnauthenticatedBlobDownloadToken.generate();
            store.put(new TokenKey(accountId, blobId), new StoredToken(token, username, clock.instant().plus(tokenTtl)));
            return token;
        });
    }

    @Override
    public Mono<Optional<Username>> check(AccountId accountId, BlobId blobId, UnauthenticatedBlobDownloadToken token) {
        return Mono.fromCallable(() -> {
            TokenKey key = new TokenKey(accountId, blobId);
            return Optional.ofNullable(store.get(key))
                .filter(storedToken -> !isExpired(key, storedToken))
                .filter(storedToken -> storedToken.token().equals(token))
                .map(StoredToken::username);
        });
    }

    boolean isStored(AccountId accountId, BlobId blobId) {
        return store.containsKey(new TokenKey(accountId, blobId));
    }

    private boolean isExpired(TokenKey key, StoredToken storedToken) {
        if (isExpired(storedToken)) {
            store.remove(key, storedToken);
            return true;
        }
        return false;
    }

    private boolean isExpired(StoredToken storedToken) {
        return !clock.instant().isBefore(storedToken.expiresAt());
    }
}
