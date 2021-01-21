package com.linagora.openpaas.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

public interface CliContract {
    GenericContainer<?> jamesContainer();

    @Test
    default void cliShouldWork() throws Exception {
        Container.ExecResult exec1 = jamesContainer().execInContainer("james-cli", "ListDomains");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(exec1.getExitCode()).isEqualTo(0);
            softly.assertThat(exec1.getStdout()).doesNotContain("domain.tld");
        });

        assertThat(exec1.getStdout())
            .doesNotContain("domain.tld");

        Container.ExecResult exec2 = jamesContainer().execInContainer("james-cli", "addDomain", "domain.tld");
        assertThat(exec2.getExitCode()).isEqualTo(0);

        Container.ExecResult exec3 = jamesContainer().execInContainer("james-cli", "ListDomains");
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(exec3.getExitCode()).isEqualTo(0);
            softly.assertThat(exec3.getStdout()).contains("domain.tld");
        });
    }
}
