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

import java.time.Duration;
import java.util.Optional;

import io.lettuce.core.api.reactive.RedisKeyReactiveCommands;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.api.reactive.RedisStringReactiveCommands;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import reactor.core.publisher.Mono;

public class RedisUnauthenticatedBlobDownloadTokenRepositoryCommands {
    public static RedisUnauthenticatedBlobDownloadTokenRepositoryCommands of(RedisReactiveCommands<String, String> commands, Duration commandTimeout) {
        return new RedisUnauthenticatedBlobDownloadTokenRepositoryCommands(commands, commands, commandTimeout);
    }

    public static RedisUnauthenticatedBlobDownloadTokenRepositoryCommands of(RedisClusterReactiveCommands<String, String> commands, Duration commandTimeout) {
        return new RedisUnauthenticatedBlobDownloadTokenRepositoryCommands(commands, commands, commandTimeout);
    }

    private final RedisStringReactiveCommands<String, String> stringCommands;
    private final RedisKeyReactiveCommands<String, String> keyCommands;
    private final Duration commandTimeout;

    public RedisUnauthenticatedBlobDownloadTokenRepositoryCommands(RedisStringReactiveCommands<String, String> stringCommands,
                                                                   RedisKeyReactiveCommands<String, String> keyCommands,
                                                                   Duration commandTimeout) {
        this.stringCommands = stringCommands;
        this.keyCommands = keyCommands;
        this.commandTimeout = commandTimeout;
    }

    public Mono<Void> set(String key, String value, Duration ttl) {
        return stringCommands.psetex(key, ttl.toMillis(), value)
            .timeout(commandTimeout)
            .then();
    }

    public Mono<Optional<String>> get(String key) {
        return stringCommands.get(key)
            .timeout(commandTimeout)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
    }

    Mono<Long> ttl(String key) {
        return keyCommands.pttl(key)
            .timeout(commandTimeout);
    }
}
