package com.linagora.apisix.plugin;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public interface IRevokedTokenRepository {

    void add(String sid);

    boolean exist(String sid);

    @Component
    @Scope("singleton")
    class MemoryRevokedTokenRepository implements IRevokedTokenRepository {

        private static final Cache<String, Boolean> cache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .build();

        @Override
        public void add(String sid) {
            cache.put(sid, true);
        }

        @Override
        public boolean exist(String sid) {
            return cache.getIfPresent(sid) != null;
        }
    }
}

