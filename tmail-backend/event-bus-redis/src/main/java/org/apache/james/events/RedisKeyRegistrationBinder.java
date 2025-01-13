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

package org.apache.james.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.api.reactive.RedisSetReactiveCommands;
import reactor.core.publisher.Mono;

class RedisKeyRegistrationBinder {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisKeyRegistrationBinder.class);

    private final RedisSetReactiveCommands<String, String> redisSetReactiveCommands;
    private final RegistrationQueueName registrationChannel;

    RedisKeyRegistrationBinder(RedisSetReactiveCommands<String, String> redisSetReactiveCommands,
                               RegistrationQueueName registrationChannel) {
        this.redisSetReactiveCommands = redisSetReactiveCommands;
        this.registrationChannel = registrationChannel;
    }

    Mono<Void> bind(RegistrationKey key) {
        // Use Redis Set to store 1 registrationKey -> n channel(s) mapping in Redis
        RoutingKeyConverter.RoutingKey routingKey = RoutingKeyConverter.RoutingKey.of(key);
        return redisSetReactiveCommands.sadd(routingKey.asString(), registrationChannel.asString())
            // the following log should be removed once we finished monitoring Redis event bus keys implementation
            .doOnSuccess(l -> LOGGER.info("Registered {} key-channel mapping to Redis with key {} and channel {}", l, routingKey.asString(), registrationChannel.asString()))
            .then();
    }

    Mono<Void> unbind(RegistrationKey key) {
        // delete the registrationKey -> channel mapping in Redis
        RoutingKeyConverter.RoutingKey routingKey = RoutingKeyConverter.RoutingKey.of(key);
        return redisSetReactiveCommands.srem(routingKey.asString(), registrationChannel.asString())
            // the following log should be removed once we finished monitoring Redis event bus keys implementation
            .doOnSuccess(l -> LOGGER.info("Unregistered {} key-channel mapping to Redis with key {} and channel {}", l, routingKey.asString(), registrationChannel.asString()))
            .then();
    }
}