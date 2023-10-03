package com.linagora.tmail.webadmin.cleanup;

import static org.mockito.Mockito.mock;

import org.apache.james.JsonSerializationVerifier;
import org.junit.jupiter.api.Test;

public class CleanupTrashTaskSerializationTest {

    CleanupTrashService cleanupTrashService = mock(CleanupTrashService.class);

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(CleanupTrashTaskDTO.module(cleanupTrashService))
            .bean(new CleanupTrashTask(cleanupTrashService, RunningOptions.of(9)))
            .json("{\"type\": \"cleanup-trash\",\"runningOptions\":{\"usersPerSecond\":9}}")
            .verify();
    }
}
