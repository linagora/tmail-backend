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
 *******************************************************************/

package com.linagora.tmail.james.jmap.blob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.apache.james.backends.redis.RedisClientFactory;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.UnauthenticatedBlobAccessConfiguration;

public abstract class RedisUnauthenticatedBlobDownloadTokenRepositoryContract implements UnauthenticatedBlobDownloadTokenRepositoryContract {
    protected static final AccountId ACCOUNT_ID = AccountId.fromString("accountId");
    protected static final PlainBlobId BLOB_ID = new PlainBlobId.Factory().parse("blobId");

    private static final Duration TOKEN_TTL = Duration.ofSeconds(1);

    private RedisUnauthenticatedBlobDownloadTokenRepository repository;
    private RedisUnauthenticatedBlobDownloadTokenRepositoryCommands redisCommands;

    public abstract RedisConfiguration redisConfiguration();

    @BeforeEach
    void setUp() {
        RedisConfiguration redisConfiguration = redisConfiguration();
        RedisClientFactory redisClientFactory = new RedisClientFactory(FileSystemImpl.forTesting(), redisConfiguration);
        redisCommands = new RedisUnauthenticatedBlobDownloadTokenRepositoryModule()
            .provideRedisUnauthenticatedBlobDownloadTokenRepositoryCommands(redisClientFactory, redisConfiguration);

        repository = new RedisUnauthenticatedBlobDownloadTokenRepository(redisCommands, new UnauthenticatedBlobAccessConfiguration(TOKEN_TTL));
    }

    @Override
    public UnauthenticatedBlobDownloadTokenRepository testee() {
        return repository;
    }

    @Override
    public void advanceClockAfterTtl() {
        try {
            Thread.sleep(TOKEN_TTL.toMillis() + 100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Test
    public void generateShouldStoreHashedTokenWithTtl() {
        UnauthenticatedBlobDownloadToken token = repository.generate(ACCOUNT_ID, BLOB_ID).block();
        String redisKey = RedisUnauthenticatedBlobDownloadTokenRepository.resolveKey(ACCOUNT_ID, BLOB_ID);

        String storedTokenHash = redisCommands.get(redisKey).block().orElseThrow();

        assertThat(storedTokenHash)
            .isEqualTo(RedisUnauthenticatedBlobDownloadTokenRepository.hashToken(token));
        assertThat(redisCommands.ttl(redisKey).block())
            .isPositive();
    }

    @Test
    public void generateShouldOverwriteStoredHashForSameBlob() {
        repository.generate(ACCOUNT_ID, BLOB_ID).block();
        UnauthenticatedBlobDownloadToken secondToken = repository.generate(ACCOUNT_ID, BLOB_ID).block();
        String redisKey = RedisUnauthenticatedBlobDownloadTokenRepository.resolveKey(ACCOUNT_ID, BLOB_ID);

        assertThat(redisCommands.get(redisKey).block().orElseThrow())
            .isEqualTo(RedisUnauthenticatedBlobDownloadTokenRepository.hashToken(secondToken));
    }
}
