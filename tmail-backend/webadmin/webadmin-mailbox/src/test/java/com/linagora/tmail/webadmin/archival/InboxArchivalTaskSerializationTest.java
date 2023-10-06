package com.linagora.tmail.webadmin.archival;

import static org.mockito.Mockito.mock;

import org.apache.james.JsonSerializationVerifier;
import org.junit.jupiter.api.Test;

class InboxArchivalTaskSerializationTest {
    private final InboxArchivalService inboxArchivalService = mock(InboxArchivalService.class);

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(InboxArchivalTaskDTO.module(inboxArchivalService))
            .bean(new InboxArchivalTask(inboxArchivalService))
            .json("{\"type\": \"InboxArchivalTask\"}")
            .verify();
    }
}
