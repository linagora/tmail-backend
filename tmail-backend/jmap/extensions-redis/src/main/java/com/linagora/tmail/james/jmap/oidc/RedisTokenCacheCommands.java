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

    public static RedisTokenCacheCommands of(RedisReactiveCommands<String, String> commands, Duration commandTimeout) {
        return new RedisTokenCacheCommands(commands, commands, commands, commandTimeout);
    }

    public static RedisTokenCacheCommands of(RedisClusterReactiveCommands<String, String> commands, Duration commandTimeout) {
        return new RedisTokenCacheCommands(commands, commands, commands, commandTimeout);
    }

    private final RedisKeyReactiveCommands<String, String> keyCommand;
    private final RedisListReactiveCommands<String, String> listCommand;
    private final RedisHashReactiveCommands<String, String> hashCommand;
    private final Duration commandTimeout;

    public RedisTokenCacheCommands(RedisKeyReactiveCommands<String, String> keyCommand,
                                   RedisListReactiveCommands<String, String> listCommand,
                                   RedisHashReactiveCommands<String, String> hashCommand,
                                   Duration commandTimeout) {
        this.keyCommand = keyCommand;
        this.listCommand = listCommand;
        this.hashCommand = hashCommand;
        this.commandTimeout = commandTimeout;
    }

    public Flux<String> lrange(String key) {
        return listCommand.lrange(key, 0, -1)
            .timeout(commandTimeout);
    }

    public Mono<Long> rpush(String key, String... values) {
        return listCommand.rpush(key, values)
            .timeout(commandTimeout);
    }

    public Mono<Void> del(String... key) {
        return keyCommand.del(key)
            .timeout(commandTimeout)
            .then();
    }

    public Mono<Void> expire(String key, Duration duration) {
        return keyCommand.expire(key, duration)
            .timeout(commandTimeout)
            .then();
    }

    public Mono<Void> hset(String key, Map<String, String> map) {
        return hashCommand.hset(key, map)
            .timeout(commandTimeout)
            .then();
    }

    public Flux<KeyValue<String, String>> hgetall(String key) {
        return hashCommand.hgetall(key)
            .timeout(commandTimeout);
    }
}
