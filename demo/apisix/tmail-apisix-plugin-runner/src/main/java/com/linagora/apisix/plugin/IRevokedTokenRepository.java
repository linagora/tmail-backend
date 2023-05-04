package com.linagora.apisix.plugin;

import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public interface IRevokedTokenRepository {

    void add(String sid);

    boolean exist(String sid);

    class MemoryRevokedTokenRepository implements IRevokedTokenRepository {

        private final Cache<String, Boolean> cache;

        public MemoryRevokedTokenRepository() {
            this.cache = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .build();
        }

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

