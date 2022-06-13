package com.linagora.tmail.deployment;

import java.time.Duration;

import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;

public class TestContainerWaitStrategy extends LogMessageWaitStrategy {
    public static final Duration STARTUP_TIMEOUT_DURATION = Duration.ofMinutes(3);

    public static final WaitStrategy WAIT_STRATEGY =  new LogMessageWaitStrategy().withRegEx(".*JAMES server started.*\\n").withTimes(1)
                .withStartupTimeout(STARTUP_TIMEOUT_DURATION);
}
