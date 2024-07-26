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

import io.lettuce.core.ReadFrom;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.sync.RedisStringCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.masterreplica.MasterReplica;
import io.lettuce.core.masterreplica.StatefulRedisMasterReplicaConnection;

@Configuration
public class AppConfiguration {
    private final Logger logger = LoggerFactory.getLogger(AppConfiguration.class);

    public static final String TOPOLOGY_STANDALONE = "standalone";
    public static final String TOPOLOGY_CLUSTER = "cluster";
    public static final String TOPOLOGY_SENTINEL = "sentinel";
    public static final String TOPOLOGY_MASTER_REPLICA = "master_replica";

    @Value("${redis.url}")
    private String redisUrl;

    @Value("${redis.password}")
    private String redisPassword;

    @Value("${redis.timeout:5000}")
    private Integer redisTimeout;

    @Value("${redis.ignoreErrors:true}")
    private Boolean ignoreRedisErrors;

    @Value("${redis.topology}")
    private String redisTopology;

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

        logger.info("The plugin using redis {} for storage revoked tokens.\n" +
            "URI = {}\n" +
            "ignoreErrors = {}\n" +
            "redisTimeout = {}\n", redisTopology, redisUrl, ignoreRedisErrors, Duration.ofMillis(redisTimeout));

        switch (redisTopology) {
            case TOPOLOGY_CLUSTER:
                return initRedisCommandCluster(redisUrl, redisPassword, timeoutDuration);
            case TOPOLOGY_SENTINEL:
                return initRedisCommandSentinel(redisUrl, timeoutDuration);
            case TOPOLOGY_MASTER_REPLICA:
                return initRedisCommandMasterReplica(redisUrl, redisPassword, timeoutDuration);
            default:
                return initRedisCommandStandalone(redisUrl, redisPassword, timeoutDuration);
        }
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
        List<RedisURI> redisURIList = buildRedisUriList(redisUrl, redisPassword, redisTimeout);
        return RedisClusterClient.create(redisURIList).connect().sync();
    }

    public static RedisStringCommands<String, String> initRedisCommandMasterReplica(String redisUrl,
                                                                                    String redisPassword,
                                                                                    Duration redisTimeout) {
        List<RedisURI> redisURIList = buildRedisUriList(redisUrl, redisPassword, redisTimeout);

        RedisClient redisClient = RedisClient.create();
        StatefulRedisMasterReplicaConnection<String, String> connection = MasterReplica
            .connect(redisClient, StringCodec.UTF8, redisURIList);
        connection.setReadFrom(ReadFrom.MASTER_PREFERRED);
        return connection.sync();
    }

    public static List<RedisURI> buildRedisUriList(String redisUrl, String redisPassword, Duration redisTimeout) {
        return Arrays.stream(redisUrl.split(","))
            .map(url -> buildRedisUri(url, redisPassword))
            .map(RedisURI::create)
            .peek(uri -> uri.setTimeout(redisTimeout))
            .collect(Collectors.toList());
    }

    private static String buildRedisUri(String redisUrl, String redisPassword) {
        if (StringUtils.hasText(redisPassword)) {
            return String.format("redis://%s@%s", redisPassword, redisUrl);
        }
        return String.format("redis://%s", redisUrl);
    }
}
