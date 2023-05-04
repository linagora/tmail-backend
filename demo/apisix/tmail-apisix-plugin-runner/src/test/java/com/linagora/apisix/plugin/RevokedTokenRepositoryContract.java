package com.linagora.apisix.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

interface RevokedTokenRepositoryContract {

    IRevokedTokenRepository testee();

    @Test
    default void existShouldReturnFalseWhenDoesNotExist() {
        assertThat(testee().exist("sid1")).isFalse();
    }

    @Test
    default void existShouldReturnTrueWhenExist() {
        String sid = "sid1";
        testee().add(sid);
        assertThat(testee().exist(sid)).isTrue();
    }

    @Test
    default void addShouldBeIdempotent() {
        String sid = "sid1";
        testee().add(sid);
        assertThatCode(() -> testee().add(sid)).doesNotThrowAnyException();
        assertThat(testee().exist(sid)).isTrue();
    }

    @Test
    default void existShouldCheckingAssignKey() {
        testee().add("sid2");
        assertThat(testee().exist("sid1")).isFalse();
    }

    class MemoryRevokedTokenRepositoryTest implements RevokedTokenRepositoryContract {
        IRevokedTokenRepository.MemoryRevokedTokenRepository memoryRevokedTokenRepository;

        @BeforeEach
        void setup() {
            memoryRevokedTokenRepository = new IRevokedTokenRepository.MemoryRevokedTokenRepository();
        }

        @Override
        public IRevokedTokenRepository testee() {
            return memoryRevokedTokenRepository;
        }
    }
}
