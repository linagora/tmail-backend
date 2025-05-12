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

package com.linagora.tmail.james.jmap.oidc;

import java.time.Duration;
import java.util.Map;

import io.lettuce.core.KeyValue;
import io.lettuce.core.api.reactive.RedisHashReactiveCommands;
import io.lettuce.core.api.reactive.RedisKeyReactiveCommands;
import io.lettuce.core.api.reactive.RedisListReactiveCommands;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RedisTokenCacheCommands {

    public static RedisTokenCacheCommands of(RedisReactiveCommands<String, String> commands) {
        return new RedisTokenCacheCommands(commands, commands, commands);
    }

    public static RedisTokenCacheCommands of(RedisClusterReactiveCommands<String, String> commands) {
        return new RedisTokenCacheCommands(commands, commands, commands);
    }

    private static final Duration REDIS_REACTOR_TIMEOUT = Duration.ofSeconds(3);

    private final RedisKeyReactiveCommands<String, String> keyCommand;
    private final RedisListReactiveCommands<String, String> listCommand;
    private final RedisHashReactiveCommands<String, String> hashCommand;

    public RedisTokenCacheCommands(RedisKeyReactiveCommands<String, String> keyCommand,
                                   RedisListReactiveCommands<String, String> listCommand,
                                   RedisHashReactiveCommands<String, String> hashCommand) {
        this.keyCommand = keyCommand;
        this.listCommand = listCommand;
        this.hashCommand = hashCommand;
    }

    public Flux<String> lrange(String key) {
        return listCommand.lrange(key, 0, -1)
            .timeout(REDIS_REACTOR_TIMEOUT);
    }

    public Mono<Long> rpush(String key, String... values) {
        return listCommand.rpush(key, values)
            .timeout(REDIS_REACTOR_TIMEOUT);
    }

    public Mono<Void> del(String... key) {
        return keyCommand.del(key)
            .timeout(REDIS_REACTOR_TIMEOUT)
            .then();
    }

    public Mono<Void> expire(String key, Duration duration) {
        return keyCommand.expire(key, duration)
            .timeout(REDIS_REACTOR_TIMEOUT)
            .then();
    }

    public Mono<Void> hset(String key, Map<String, String> map) {
        return hashCommand.hset(key, map)
            .timeout(REDIS_REACTOR_TIMEOUT)
            .then();
    }

    public Flux<KeyValue<String, String>> hgetall(String key) {
        return hashCommand.hgetall(key)
            .timeout(REDIS_REACTOR_TIMEOUT);
    }
}
