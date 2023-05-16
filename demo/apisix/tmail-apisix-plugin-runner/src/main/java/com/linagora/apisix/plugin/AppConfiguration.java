package com.linagora.apisix.plugin;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.sync.RedisStringCommands;
import io.lettuce.core.cluster.RedisClusterClient;

@Configuration
public class AppConfiguration {
    private final Logger logger = LoggerFactory.getLogger(AppConfiguration.class);

    @Value("${redis.url}")
    private String redisUrl;

    @Value("${redis.password}")
    private String redisPassword;

    @Value("${redis.cluster.enable}")
    private Boolean redisClusterEnable;

    @Primary
    @Bean
    public IRevokedTokenRepository revokedTokenRepository() {
        if (StringUtils.hasText(redisUrl)) {
            logger.info("The plugin using redis for storage revoked tokens. \nURI = {}\nCluster.enable = {}",
                redisUrl, redisClusterEnable);
            return new RedisRevokedTokenRepository(initRedisCommand(redisUrl, redisPassword, redisClusterEnable));
        }

        logger.info("The plugin using local memory for storage revoked tokens");
        return new IRevokedTokenRepository.MemoryRevokedTokenRepository();
    }

    public static RedisStringCommands<String, String> initRedisCommand(String redisUrl, String redisPassword, boolean redisClusterEnable) {
        List<RedisURI> redisURIList = Arrays.stream(redisUrl.split(","))
            .map(url -> buildRedisUri(url, redisPassword))
            .map(RedisURI::create)
            .collect(Collectors.toList());
        if (redisURIList.size() > 1 && !redisClusterEnable) {
            throw new IllegalArgumentException("Can not provide multi Redis URI when cluster.enable=false");
        }

        if (!redisClusterEnable) {
            return RedisClient.create(redisURIList.get(0)).connect().sync();
        }
        return RedisClusterClient.create(redisURIList).connect().sync();
    }

    private static String buildRedisUri(String redisUrl, String redisPassword) {
        if (StringUtils.hasText(redisPassword)) {
            return String.format("redis://%s@%s", redisPassword, redisUrl);
        }
        return String.format("redis://%s", redisUrl);
    }
}
