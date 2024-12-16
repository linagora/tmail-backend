package com.linagora.apisix.plugin;

import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import reactor.core.publisher.Mono;

public interface IRevokedTokenRepository {

    Mono<Void> add(String sid);

    Mono<Boolean> exist(String sid);

    class MemoryRevokedTokenRepository implements IRevokedTokenRepository {

        private final Cache<String, Boolean> cache;

        public MemoryRevokedTokenRepository() {
            this.cache = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .build();
        }

        @Override
        public Mono<Void> add(String sid) {
            return Mono.fromRunnable(() -> cache.put(sid, true));
        }

        @Override
        public Mono<Boolean> exist(String sid) {
            return Mono.fromCallable(() -> cache.getIfPresent(sid) != null);
        }
    }
}

