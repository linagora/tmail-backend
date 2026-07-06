package com.linagora.tmail.saas.filter;

import static com.linagora.tmail.saas.api.SaaSAccountRepositoryContract.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class SaaSPayingUserTest {
    @Test
    void shouldReturnTrueWhenUserIsPaying() {
        MemorySaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        Mono.from(saaSAccountRepository.upsertSaasAccount(BOB, new SaaSAccount(true, true))).block();

        SaaSPayingUser testee = new SaaSPayingUser(saaSAccountRepository);

        assertThat(testee.isEligible(BOB).block()).isTrue();
    }

    @Test
    void shouldReturnFalseWhenUserIsNotPaying() {
        MemorySaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        Mono.from(saaSAccountRepository.upsertSaasAccount(BOB, new SaaSAccount(true, false))).block();

        SaaSPayingUser testee = new SaaSPayingUser(saaSAccountRepository);

        assertThat(testee.isEligible(BOB).block()).isFalse();
    }

    @Test
    void shouldReturnFalseWhenNoSaaSAccountStored() {
        MemorySaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();

        SaaSPayingUser testee = new SaaSPayingUser(saaSAccountRepository);

        assertThat(testee.isEligible(BOB).block()).isFalse();
    }
}
