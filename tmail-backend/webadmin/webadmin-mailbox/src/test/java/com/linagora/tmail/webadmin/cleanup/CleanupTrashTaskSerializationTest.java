package com.linagora.tmail.webadmin.cleanup;

import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public class CleanupTrashTaskSerializationTest {

    static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    static final String SERIALIZED_ADDITIONAL_INFORMATION = "{" +
        "\"type\":\"cleanup-trash\"," +
        "\"processedUsersCount\":12," +
        "\"deletedMessagesCount\":55," +
        "\"failedUsers\":[\"bob@localhost\"]," +
        "\"runningOptions\":{\"usersPerSecond\":1}," +
        "\"timestamp\":\"2018-11-13T12:00:55Z\"" +
        "}";

    CleanupTrashService cleanupTrashService = mock(CleanupTrashService.class);

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(CleanupTrashTaskDTO.module(cleanupTrashService))
            .bean(new CleanupTrashTask(cleanupTrashService, RunningOptions.of(9)))
            .json("{\"type\":\"cleanup-trash\",\"runningOptions\":{\"usersPerSecond\":9}}")
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(CleanupTrashTaskAdditionalInformationDTO.module())
            .bean(new CleanupTaskDetails(TIMESTAMP, 12, 55,
                ImmutableList.of("bob@localhost"), RunningOptions.DEFAULT))
            .json(SERIALIZED_ADDITIONAL_INFORMATION)
            .verify();
    }
}
