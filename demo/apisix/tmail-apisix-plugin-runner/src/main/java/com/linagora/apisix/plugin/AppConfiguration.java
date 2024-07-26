package com.linagora.apisix.plugin;

import java.time.Duration;
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

    @Value("${redis.timeout:5000}")
    private Integer redisTimeout;

    @Value("${redis.ignoreErrors:true}")
    private Boolean ignoreRedisErrors;

    @Value("${redis.cluster.enable}")
    private Boolean redisClusterEnable;

    @Value("${redis.sentinel.enable}")
    private Boolean redisSentinelEnable;

    @Primary
    @Bean
    public IRevokedTokenRepository revokedTokenRepository() {
        if (StringUtils.hasText(redisUrl)) {
            return new RedisRevokedTokenRepository(initRedisCommand(), ignoreRedisErrors);
        }
        logger.info("The plugin using local memory for storage revoked tokens");
        return new IRevokedTokenRepository.MemoryRevokedTokenRepository();
    }

    private RedisStringCommands<String, String> initRedisCommand() {
        Duration timeoutDuration = Duration.ofMillis(redisTimeout);
        if (redisClusterEnable) {
            logRedisConfiguration("cluster");
            return initRedisCommandCluster(redisUrl, redisPassword, timeoutDuration);
        } else if (redisSentinelEnable) {
            logRedisConfiguration("sentinel");
            return initRedisCommandSentinel(redisUrl, timeoutDuration);
        } else {
            logRedisConfiguration("standalone");
            return initRedisCommandStandalone(redisUrl, redisPassword, timeoutDuration);
        }
    }

    private void logRedisConfiguration(String type) {
        logger.info("The plugin using redis {} for storage revoked tokens.\n" +
            "URI = {}\n" +
            "ignoreErrors = {}\n" +
            "redisTimeout = {}\n", type, redisUrl, ignoreRedisErrors, Duration.ofMillis(redisTimeout));
    }

    public static RedisStringCommands<String, String> initRedisCommandSentinel(String redisUrl,
                                                                               Duration redisTimeout) {
        RedisURI redisURI = RedisURI.create(redisUrl);
        redisURI.setTimeout(redisTimeout);
        return RedisClient.create(redisURI).connect().sync();
    }

    public static RedisStringCommands<String, String> initRedisCommandStandalone(String redisUrl,
                                                                                 String redisPassword,
                                                                                 Duration redisTimeout) {
        String[] redisUrlParts = redisUrl.split(":");
        RedisURI redisURI = RedisURI.builder()
            .withHost(redisUrlParts[0])
            .withPort(Integer.parseInt(redisUrlParts[1]))
            .withPassword(redisPassword.toCharArray())
            .withTimeout(redisTimeout)
            .build();

        return RedisClient.create(redisURI).connect().sync();
    }

    public static RedisStringCommands<String, String> initRedisCommandCluster(String redisUrl,
                                                                              String redisPassword,
                                                                              Duration redisTimeout) {
        List<RedisURI> redisURIList = Arrays.stream(redisUrl.split(","))
            .map(url -> buildRedisUri(url, redisPassword))
            .map(RedisURI::create)
            .peek(uri -> uri.setTimeout(redisTimeout))
            .collect(Collectors.toList());

        return RedisClusterClient.create(redisURIList).connect().sync();
    }

    private static String buildRedisUri(String redisUrl, String redisPassword) {
        if (StringUtils.hasText(redisPassword)) {
            return String.format("redis://%s@%s", redisPassword, redisUrl);
        }
        return String.format("redis://%s", redisUrl);
    }
}
