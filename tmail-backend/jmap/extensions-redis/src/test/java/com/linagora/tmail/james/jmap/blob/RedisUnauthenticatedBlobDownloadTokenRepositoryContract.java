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
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.UnauthenticatedBlobAccessConfiguration;
import com.linagora.tmail.james.jmap.redis.RedisReactiveCommandsFactory;

public abstract class RedisUnauthenticatedBlobDownloadTokenRepositoryContract implements UnauthenticatedBlobDownloadTokenRepositoryContract {
    protected static final AccountId ACCOUNT_ID = AccountId.fromString("accountId");
    protected static final PlainBlobId BLOB_ID = new PlainBlobId.Factory().parse("blobId");
    protected static final Username USERNAME = Username.of("bob@domain.tld");

    private static final Duration TOKEN_TTL = Duration.ofSeconds(1);

    private RedisUnauthenticatedBlobDownloadTokenRepository repository;
    private RedisUnauthenticatedBlobDownloadTokenRepositoryCommands redisCommands;

    public abstract RedisConfiguration redisConfiguration();

    @BeforeEach
    void setUp() {
        RedisConfiguration redisConfiguration = redisConfiguration();
        RedisClientFactory redisClientFactory = new RedisClientFactory(FileSystemImpl.forTesting(), redisConfiguration);
        redisCommands = new RedisUnauthenticatedBlobDownloadTokenRepositoryModule()
            .provideRedisUnauthenticatedBlobDownloadTokenRepositoryCommands(new RedisReactiveCommandsFactory(redisClientFactory, redisConfiguration),
                RedisUnauthenticatedBlobDownloadTokenRepositoryConfiguration.DEFAULT);

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
    public void generateShouldStoreHashedTokenAndEncodedUsernameWithTtl() {
        UnauthenticatedBlobDownloadToken token = repository.generate(ACCOUNT_ID, BLOB_ID, USERNAME).block();
        String redisKey = RedisUnauthenticatedBlobDownloadTokenRepository.resolveKey(ACCOUNT_ID, BLOB_ID);

        String storedValue = redisCommands.get(redisKey).block().orElseThrow();

        assertThat(storedValue)
            .isEqualTo(RedisUnauthenticatedBlobDownloadTokenRepository.formatValue(token, USERNAME));
        assertThat(redisCommands.ttl(redisKey).block())
            .isPositive();
    }

    @Test
    public void generateShouldOverwriteStoredHashForSameBlob() {
        repository.generate(ACCOUNT_ID, BLOB_ID, USERNAME).block();
        UnauthenticatedBlobDownloadToken secondToken = repository.generate(ACCOUNT_ID, BLOB_ID, USERNAME).block();
        String redisKey = RedisUnauthenticatedBlobDownloadTokenRepository.resolveKey(ACCOUNT_ID, BLOB_ID);

        assertThat(redisCommands.get(redisKey).block().orElseThrow())
            .isEqualTo(RedisUnauthenticatedBlobDownloadTokenRepository.formatValue(secondToken, USERNAME));
    }

    @Test
    public void checkShouldReturnEmptyWhenStoredUsernameIsMalformed() {
        UnauthenticatedBlobDownloadToken token = UnauthenticatedBlobDownloadToken.generate();
        String redisKey = RedisUnauthenticatedBlobDownloadTokenRepository.resolveKey(ACCOUNT_ID, BLOB_ID);
        redisCommands.set(redisKey, RedisUnauthenticatedBlobDownloadTokenRepository.hashToken(token) + ":not valid base64", TOKEN_TTL).block();

        assertThat(repository.check(ACCOUNT_ID, BLOB_ID, token).block())
            .isEmpty();
    }
}
