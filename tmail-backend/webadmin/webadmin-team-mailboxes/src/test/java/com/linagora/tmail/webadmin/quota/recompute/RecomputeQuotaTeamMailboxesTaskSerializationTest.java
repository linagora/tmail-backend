package com.linagora.tmail.webadmin.quota.recompute;

import static org.mockito.Mockito.mock;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Domain;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RecomputeQuotaTeamMailboxesTaskSerializationTest {
    RecomputeQuotaTeamMailboxesService recomputeQuotaTeamMailboxesService;

    @BeforeEach
    void setUp() {
        recomputeQuotaTeamMailboxesService = mock(RecomputeQuotaTeamMailboxesService.class);
    }

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(RecomputeQuotaTeamMailboxesTaskDTO.module(recomputeQuotaTeamMailboxesService))
            .bean(new RecomputeQuotaTeamMailboxesTask(
                recomputeQuotaTeamMailboxesService,
                Domain.of("linagora.com")))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/recompute_quota_team_mailboxes.task.json"))
            .verify();
    }
}
