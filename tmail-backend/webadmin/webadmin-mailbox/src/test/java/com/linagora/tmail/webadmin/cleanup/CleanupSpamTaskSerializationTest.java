package com.linagora.tmail.webadmin.cleanup;

import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public class CleanupSpamTaskSerializationTest {
    static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    static final String SERIALIZED_ADDITIONAL_INFORMATION = "{" +
        "\"type\":\"cleanup-spam\"," +
        "\"processedUsersCount\":12," +
        "\"deletedMessagesCount\":55," +
        "\"failedUsers\":[\"bob@localhost\"]," +
        "\"runningOptions\":{\"usersPerSecond\":1}," +
        "\"timestamp\":\"2018-11-13T12:00:55Z\"" +
        "}";

    CleanupService cleanupService = mock(CleanupService.class);

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(CleanupSpamTaskDTO.module(cleanupService))
            .bean(new CleanupSpamTask(cleanupService, RunningOptions.of(9)))
            .json("{\"type\":\"cleanup-spam\",\"runningOptions\":{\"usersPerSecond\":9}}")
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(CleanupSpamTaskAdditionalInformationDTO.module())
            .bean(new CleanupSpamTaskDetails(TIMESTAMP, 12, 55,
                ImmutableList.of("bob@localhost"), RunningOptions.DEFAULT))
            .json(SERIALIZED_ADDITIONAL_INFORMATION)
            .verify();
    }
}
