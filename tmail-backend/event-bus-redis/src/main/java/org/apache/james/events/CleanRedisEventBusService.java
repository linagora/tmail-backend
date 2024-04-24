package org.apache.james.events;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import io.lettuce.core.KeyScanArgs;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.reactive.RedisKeyReactiveCommands;
import io.lettuce.core.api.reactive.RedisSetReactiveCommands;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CleanRedisEventBusService {
    public static class Context {
        private final AtomicLong totalBindings;
        private final AtomicLong danglingBindings;
        private final AtomicLong cleanedBindings;

        public Context() {
            this.totalBindings = new AtomicLong();
            this.cleanedBindings = new AtomicLong();
            this.danglingBindings = new AtomicLong();
        }

        void increaseScannedBindingsCount() {
            totalBindings.incrementAndGet();
        }

        void increaseDanglingBindingsCount(long count) {
            danglingBindings.addAndGet(count);
        }

        void increaseCleanedBindingsCount(long count) {
            cleanedBindings.addAndGet(count);
        }

        public long getTotalBindings() {
            return totalBindings.get();
        }

        public long getDanglingBindings() {
            return danglingBindings.get();
        }

        public long getCleanedBindings() {
            return cleanedBindings.get();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanRedisEventBusService.class);

    private final RedisSetReactiveCommands<String, String> redisSetCommands;
    private final RedisPubSubReactiveCommands<String, String> redisPubSubCommands;
    private final RedisKeyReactiveCommands<String, String> redisKeyCommands;
    private final RoutingKeyConverter routingKeyConverter;
    private Context context;

    @Inject
    @Singleton
    public CleanRedisEventBusService(RedisEventBusClientFactory redisEventBusClientFactory,
                                     RoutingKeyConverter routingKeyConverter) {
        this.redisSetCommands = redisEventBusClientFactory.createRedisSetCommand();
        this.redisPubSubCommands = redisEventBusClientFactory.createRedisPubSubCommand();
        this.redisKeyCommands = redisEventBusClientFactory.createRedisKeyCommand();
        this.routingKeyConverter = routingKeyConverter;
    }

    public Mono<Void> cleanUp() {
        this.context = new Context();

        return listActiveRedisChannels()
            .collectList()
            .flatMap(activeChannels -> listRedisSetKeys()
                .filter(this::isEventBusBindingKey)
                .flatMap(key -> listElementsOfSet(key)
                        .doOnNext(channel -> context.increaseScannedBindingsCount())
                        .filter(channel -> !activeChannels.contains(channel))
                        .collectList()
                        .doOnNext(inactiveChannels -> context.increaseDanglingBindingsCount(inactiveChannels.size()))
                        .flatMap(deleteBindingsToInactiveChannels(key)),
                    ReactorUtils.DEFAULT_CONCURRENCY)
                .then())
            .doOnSuccess(any -> mdcLogWithContext()
                .log(logger -> logger.info("Clean dangling eventbus bindings in Redis successfully")))
            .doOnError(e -> mdcLogWithContext()
                .log(logger -> logger.error("Failed cleaning dangling eventbus bindings in Redis", e)));
    }

    private Flux<String> listActiveRedisChannels() {
        return redisPubSubCommands.pubsubChannels();
    }

    private Flux<String> listRedisSetKeys() {
        return redisKeyCommands.scan(ScanCursor.INITIAL, KeyScanArgs.Builder.type("set"))
            .flatMapIterable(KeyScanCursor::getKeys);
    }

    private Flux<String> listElementsOfSet(String registrationKey) {
        return redisSetCommands.smembers(registrationKey);
    }

    private Function<List<String>, Mono<Long>> deleteBindingsToInactiveChannels(String registrationKey) {
        return inactiveChannels -> {
            if (inactiveChannels.isEmpty()) {
                return Mono.empty();
            }
            return redisSetCommands.srem(registrationKey, inactiveChannels.toArray(new String[0]))
                .doOnNext(context::increaseCleanedBindingsCount);
        };
    }

    private StructuredLogger mdcLogWithContext() {
        MDCStructuredLogger mdcStructuredLogger = MDCStructuredLogger.forLogger(LOGGER);
        mdcStructuredLogger.field("totalBindings", String.valueOf(context.totalBindings.get()));
        mdcStructuredLogger.field("danglingBindings", String.valueOf(context.danglingBindings.get()));
        mdcStructuredLogger.field("cleanedBindings", String.valueOf(context.cleanedBindings.get()));
        return mdcStructuredLogger;
    }

    private boolean isEventBusBindingKey(String aSetKey) {
        try {
            routingKeyConverter.toRegistrationKey(aSetKey);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    @VisibleForTesting
    public Context getContext() {
        return context;
    }
}
