package com.linagora.tmail.james.jmap.service.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.junit.jupiter.api.Test;

class LinagoraServicesDiscoveryConfigurationTest {
    @Test
    void shouldSucceedCase() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("linshareApiUrl", "https://linshare.linagora.com/linshare/webservice");
        configuration.addProperty("linToApiUrl", "https://linto.ai/demo");
        configuration.addProperty("linToApiKey", "apiKey");
        configuration.addProperty("twakeApiUrl", "https://api.twake.app");

        LinagoraServicesDiscoveryConfiguration servicesDiscoveryConfiguration = LinagoraServicesDiscoveryConfiguration.from(configuration);
        assertThat(servicesDiscoveryConfiguration.linShareApiUrl()).hasToString("https://linshare.linagora.com/linshare/webservice");
        assertThat(servicesDiscoveryConfiguration.linToApiUrl()).hasToString("https://linto.ai/demo");
        assertThat(servicesDiscoveryConfiguration.linToApiKey()).hasToString("apiKey");
        assertThat(servicesDiscoveryConfiguration.twakeApiUrl()).hasToString("https://api.twake.app");
    }

    @Test
    void shouldThrowWhenMissingLinShareURL() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("linToApiUrl", "https://linto.ai/demo");
        configuration.addProperty("linToApiKey", "apiKey");
        configuration.addProperty("twakeApiUrl", "https://api.twake.app");

        assertThatCode(() -> LinagoraServicesDiscoveryConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Missing LinShare URL configuration");
    }

    @Test
    void shouldThrowWhenMissingLinToURL() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("linshareApiUrl", "https://linshare.linagora.com/linshare/webservice");
        configuration.addProperty("linToApiKey", "apiKey");
        configuration.addProperty("twakeApiUrl", "https://api.twake.app");

        assertThatCode(() -> LinagoraServicesDiscoveryConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Missing LinTo URL configuration");
    }

    @Test
    void shouldThrowWhenMissingLinToApiKey() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("linshareApiUrl", "https://linshare.linagora.com/linshare/webservice");
        configuration.addProperty("linToApiUrl", "https://linto.ai/demo");
        configuration.addProperty("twakeApiUrl", "https://api.twake.app");

        assertThatCode(() -> LinagoraServicesDiscoveryConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Missing LinTo API Key configuration");
    }

    @Test
    void shouldThrowWhenMissingTwakeUrl() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("linshareApiUrl", "https://linshare.linagora.com/linshare/webservice");
        configuration.addProperty("linToApiUrl", "https://linto.ai/demo");
        configuration.addProperty("linToApiKey", "apiKey");

        assertThatCode(() -> LinagoraServicesDiscoveryConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Missing Twake URL configuration");
    }
}
