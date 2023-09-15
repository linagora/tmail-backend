package com.linagora.tmail.james.jmap.label;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

class CassandraLabelChangesConfigurationTest {
    private static final Duration TOO_BIG_TTL = Duration.ofSeconds(Integer.MAX_VALUE + 1L);

    @Test
    void fromShouldReturnValuesFromSuppliedConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("label.change.ttl", "3 days");

        assertThat(CassandraLabelChangesConfiguration.from(configuration).labelChangeTtl())
            .isEqualTo(Duration.ofDays(3));
    }

    @Test
    void fromShouldFallbackToDefaultValueWhenEmptySuppliedConfiguration() {
        PropertiesConfiguration emptyConfiguration = new PropertiesConfiguration();

        assertThat(CassandraLabelChangesConfiguration.from(emptyConfiguration).labelChangeTtl())
            .isEqualTo(Duration.ofDays(60));
    }

    @Test
    void shouldThrowWhenConfiguredNegativeTTL() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("label.change.ttl", "-300");

        assertThatThrownBy(() -> CassandraLabelChangesConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenConfiguredZeroTTL() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("label.change.ttl", "0");

        assertThatThrownBy(() -> CassandraLabelChangesConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenConfiguredTooBigTTL() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("label.change.ttl", String.valueOf(TOO_BIG_TTL.getSeconds()));

        assertThatThrownBy(() -> CassandraLabelChangesConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
