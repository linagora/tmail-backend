/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.backends.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.es.ElasticSearchConfiguration.Credential;
import org.apache.james.backends.es.ElasticSearchConfiguration.HostScheme;
import org.apache.james.backends.es.ElasticSearchConfiguration.SSLConfiguration;
import org.apache.james.backends.es.ElasticSearchConfiguration.SSLConfiguration.SSLTrustStore;
import org.apache.james.util.Host;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

class ElasticSearchConfigurationTest {

    @Nested
    class HostSchemeTest {

        @Test
        void shouldMatchBeanContact() {
            EqualsVerifier.forClass(HostScheme.class)
                .verify();
        }
    }

    @Nested
    class CredentialTest {

        @Test
        void shouldMatchBeanContact() {
            EqualsVerifier.forClass(Credential.class)
                .verify();
        }
    }

    @Nested
    class SSLConfigurationTest {

        @Test
        void sslTrustStoreShouldMatchBeanContact() {
            EqualsVerifier.forClass(SSLTrustStore.class)
                .verify();
        }

        @Test
        void shouldMatchBeanContact() {
            EqualsVerifier.forClass(SSLConfiguration.class)
                .verify();
        }

        @Test
        void sslConfigurationShouldReturnDefaultValueWhenEmpty() throws Exception {
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

            assertThat(ElasticSearchConfiguration.fromProperties(configuration)
                    .sslConfiguration())
                .isEqualTo(SSLConfiguration.defaultBehavior());
        }

        @Test
        void sslConfigurationShouldReturnConfiguredValue() throws Exception {
            PropertiesConfiguration configuration = new PropertiesConfiguration();
            configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

            String trustStorePath = "src/test/resources/auth-es/server.jks";
            String trustStorePassword = "secret";

            configuration.addProperty("elasticsearch.hostScheme.https.sslValidationStrategy", "override");
            configuration.addProperty("elasticsearch.hostScheme.https.trustStorePath", trustStorePath);
            configuration.addProperty("elasticsearch.hostScheme.https.trustStorePassword", trustStorePassword);
            configuration.addProperty("elasticsearch.hostScheme.https.hostNameVerifier", "default");

            assertThat(ElasticSearchConfiguration.fromProperties(configuration)
                    .sslConfiguration())
                .isEqualTo(SSLConfiguration.builder()
                    .strategyOverride(SSLTrustStore.of(trustStorePath, trustStorePassword))
                    .defaultHostNameVerifier()
                    .build());
        }

        @Nested
        class WithSSLValidationStrategy {

            @Test
            void sslConfigurationShouldAcceptCaseInsensitiveStrategy() throws Exception {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

                configuration.addProperty("elasticsearch.hostScheme.https.sslValidationStrategy", "DEfault");

                assertThat(ElasticSearchConfiguration.fromProperties(configuration)
                        .sslConfiguration())
                    .isEqualTo(SSLConfiguration.defaultBehavior());
            }

            @Test
            void fromPropertiesShouldThrowWhenInvalidStrategy() {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

                configuration.addProperty("elasticsearch.hostScheme.https.sslValidationStrategy", "invalid");

                assertThatThrownBy(() -> ElasticSearchConfiguration.fromProperties(configuration))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("invalid strategy 'invalid'");
            }
        }

        @Nested
        class WithHostNameVerifier {

            @Test
            void sslConfigurationShouldReturnConfiguredValue() throws Exception {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

                configuration.addProperty("elasticsearch.hostScheme.https.hostNameVerifier", "DEFAULT");

                assertThat(ElasticSearchConfiguration.fromProperties(configuration)
                        .sslConfiguration())
                    .isEqualTo(SSLConfiguration.builder()
                        .strategyDefault()
                        .defaultHostNameVerifier()
                        .build());
            }

            @Test
            void sslConfigurationShouldAcceptCaseInsensitiveVerifier() throws Exception {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

                configuration.addProperty("elasticsearch.hostScheme.https.hostNameVerifier", "Accept_Any_Hostname");

                assertThat(ElasticSearchConfiguration.fromProperties(configuration)
                        .sslConfiguration())
                    .isEqualTo(SSLConfiguration.builder()
                        .strategyDefault()
                        .acceptAnyHostNameVerifier()
                        .build());
            }

            @Test
            void fromPropertiesShouldThrowWhenInvalidVerifier() {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

                configuration.addProperty("elasticsearch.hostScheme.https.hostNameVerifier", "invalid");

                assertThatThrownBy(() -> ElasticSearchConfiguration.fromProperties(configuration))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("invalid HostNameVerifier 'invalid'");
            }
        }

        @Nested
        class WhenDefault {

            @Test
            void sslConfigurationShouldReturnConfiguredValue() throws Exception {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

                configuration.addProperty("elasticsearch.hostScheme.https.sslValidationStrategy", "default");

                assertThat(ElasticSearchConfiguration.fromProperties(configuration)
                        .sslConfiguration())
                    .isEqualTo(SSLConfiguration.defaultBehavior());
            }
        }

        @Nested
        class WhenIgnore {

            @Test
            void sslConfigurationShouldReturnConfiguredValue() throws Exception {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

                configuration.addProperty("elasticsearch.hostScheme.https.sslValidationStrategy", "ignore");

                assertThat(ElasticSearchConfiguration.fromProperties(configuration)
                        .sslConfiguration())
                    .isEqualTo(SSLConfiguration.builder()
                        .strategyIgnore()
                        .defaultHostNameVerifier()
                        .build());
            }
        }

        @Nested
        class WhenOverride {

            @Test
            void fromPropertiesShouldThrowWhenOnlyTrustStorePathProvided() {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

                configuration.addProperty("elasticsearch.hostScheme.https.sslValidationStrategy", "override");
                configuration.addProperty("elasticsearch.hostScheme.https.trustStorePath", "/home/james/ServerTrustStore.jks");

                assertThatThrownBy(() -> ElasticSearchConfiguration.fromProperties(configuration))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("elasticsearch.hostScheme.https.trustStorePassword cannot be null when elasticsearch.hostScheme.https.trustStorePath is specified");
            }

            @Test
            void fromPropertiesShouldThrowWhenOnlyTrustStorePasswordProvided() {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

                configuration.addProperty("elasticsearch.hostScheme.https.sslValidationStrategy", "override");
                configuration.addProperty("elasticsearch.hostScheme.https.trustStorePassword", "secret");

                assertThatThrownBy(() -> ElasticSearchConfiguration.fromProperties(configuration))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("elasticsearch.hostScheme.https.trustStorePath cannot be null when elasticsearch.hostScheme.https.trustStorePassword is specified");
            }

            @Test
            void fromPropertiesShouldThrowWhenTrustStoreIsNotProvided() {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

                configuration.addProperty("elasticsearch.hostScheme.https.sslValidationStrategy", "override");

                assertThatThrownBy(() -> ElasticSearchConfiguration.fromProperties(configuration))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("OVERRIDE strategy requires trustStore to be present");
            }

            @Test
            void fromPropertiesShouldThrowWhenTrustStorePathDoesntExist() {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

                configuration.addProperty("elasticsearch.hostScheme.https.sslValidationStrategy", "override");
                configuration.addProperty("elasticsearch.hostScheme.https.trustStorePath", "/home/james/ServerTrustStore.jks");
                configuration.addProperty("elasticsearch.hostScheme.https.trustStorePassword", "password");

                assertThatThrownBy(() -> ElasticSearchConfiguration.fromProperties(configuration))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("the file '/home/james/ServerTrustStore.jks' from property 'elasticsearch.hostScheme.https.trustStorePath' doesn't exist");
            }

            @Test
            void sslConfigurationShouldReturnConfiguredValue() throws Exception {
                PropertiesConfiguration configuration = new PropertiesConfiguration();
                configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

                String trustStorePath = "src/test/resources/auth-es/server.jks";
                String trustStorePassword = "secret";

                configuration.addProperty("elasticsearch.hostScheme.https.sslValidationStrategy", "override");
                configuration.addProperty("elasticsearch.hostScheme.https.trustStorePath", trustStorePath);
                configuration.addProperty("elasticsearch.hostScheme.https.trustStorePassword", trustStorePassword);
                configuration.addProperty("elasticsearch.hostScheme.https.hostNameVerifier", "default");

                assertThat(ElasticSearchConfiguration.fromProperties(configuration)
                        .sslConfiguration())
                    .isEqualTo(SSLConfiguration.builder()
                        .strategyOverride(SSLTrustStore.of(trustStorePath, trustStorePassword))
                        .defaultHostNameVerifier()
                        .build());
            }
        }
    }

    @Test
    void elasticSearchConfigurationShouldRespectBeanContract() {
        EqualsVerifier.forClass(ElasticSearchConfiguration.class)
            .verify();
    }

    @Test
    void getNbReplicaShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 36;
        configuration.addProperty("elasticsearch.nb.replica", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.nbReplica())
            .isEqualTo(value);
    }

    @Test
    void getNbReplicaShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.nbReplica())
            .isEqualTo(ElasticSearchConfiguration.DEFAULT_NB_REPLICA);
    }

    @Test
    void getWaitForActiveShardsShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 36;
        configuration.addProperty("elasticsearch.index.waitForActiveShards", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.waitForActiveShards())
            .isEqualTo(value);
    }

    @Test
    void getWaitForActiveShardsShouldReturnConfiguredValueWhenZero() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 0;
        configuration.addProperty("elasticsearch.index.waitForActiveShards", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.waitForActiveShards())
            .isEqualTo(value);
    }

    @Test
    void getWaitForActiveShardsShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        int expectedValue = 1;
        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.waitForActiveShards())
            .isEqualTo(expectedValue);
    }

    @Test
    void getNbShardsShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 36;
        configuration.addProperty("elasticsearch.nb.shards", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.nbShards())
            .isEqualTo(value);
    }

    @Test
    void getNbShardsShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.nbShards())
            .isEqualTo(ElasticSearchConfiguration.DEFAULT_NB_SHARDS);
    }

    @Test
    void getMaxRetriesShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 36;
        configuration.addProperty("elasticsearch.retryConnection.maxRetries", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.maxRetries())
            .isEqualTo(value);
    }

    @Test
    void getMaxRetriesShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.maxRetries())
            .isEqualTo(ElasticSearchConfiguration.DEFAULT_CONNECTION_MAX_RETRIES);
    }

    @Test
    void getMinDelayShouldReturnConfiguredValue() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        int value = 36;
        configuration.addProperty("elasticsearch.retryConnection.minDelay", value);
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.minDelay())
            .isEqualTo(value);
    }

    @Test
    void getMinDelayShouldReturnDefaultValueWhenMissing() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.minDelay())
            .isEqualTo(ElasticSearchConfiguration.DEFAULT_CONNECTION_MIN_DELAY);
    }

    @Test
    void getHostsShouldReturnConfiguredHostsWhenNoPort() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String hostname = "myHost";
        configuration.addProperty("elasticsearch.hosts", hostname);

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.hosts())
            .containsOnly(Host.from(hostname, ElasticSearchConfiguration.DEFAULT_PORT));
    }

    @Test
    void getHostsShouldReturnConfiguredHostsWhenListIsUsed() throws ConfigurationException {
        String hostname = "myHost";
        String hostname2 = "myOtherHost";
        int port = 2154;
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.setListDelimiterHandler(new DefaultListDelimiterHandler(','));
        configuration.addProperty("elasticsearch.hosts", hostname + "," + hostname2 + ":" + port);

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.hosts())
            .containsOnly(Host.from(hostname, ElasticSearchConfiguration.DEFAULT_PORT),
                Host.from(hostname2, port));
    }

    @Test
    void getHostsShouldReturnConfiguredHosts() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String hostname = "myHost";
        int port = 2154;
        configuration.addProperty("elasticsearch.hosts", hostname + ":" + port);

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.hosts())
            .containsOnly(Host.from(hostname, port));
    }

    @Test
    void getHostsShouldReturnConfiguredMasterHost() throws ConfigurationException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        String hostname = "myHost";
        configuration.addProperty("elasticsearch.masterHost", hostname);
        int port = 9200;
        configuration.addProperty("elasticsearch.port", port);

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.hosts())
            .containsOnly(Host.from(hostname, port));
    }

    @Test
    void validateHostsConfigurationOptionsShouldThrowWhenNoHostSpecify() {
        assertThatThrownBy(() ->
            ElasticSearchConfiguration.validateHostsConfigurationOptions(
                Optional.empty(),
                Optional.empty(),
                ImmutableList.of()))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("You should specify either (" + ElasticSearchConfiguration.ELASTICSEARCH_MASTER_HOST +
                " and " + ElasticSearchConfiguration.ELASTICSEARCH_PORT +
                ") or " + ElasticSearchConfiguration.ELASTICSEARCH_HOSTS);
    }

    @Test
    void validateHostsConfigurationOptionsShouldThrowWhenMonoAndMultiHostSpecified() {
        assertThatThrownBy(() ->
            ElasticSearchConfiguration.validateHostsConfigurationOptions(
                Optional.of("localhost"),
                Optional.of(9200),
                ImmutableList.of("localhost:9200")))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage("You should choose between mono host set up and " + ElasticSearchConfiguration.ELASTICSEARCH_HOSTS);
    }

    @Test
    void validateHostsConfigurationOptionsShouldThrowWhenMonoHostWithoutPort() {
        assertThatThrownBy(() ->
            ElasticSearchConfiguration.validateHostsConfigurationOptions(
                Optional.of("localhost"),
                Optional.empty(),
                ImmutableList.of()))
            .isInstanceOf(ConfigurationException.class)
            .hasMessage(ElasticSearchConfiguration.ELASTICSEARCH_MASTER_HOST +
                " and " + ElasticSearchConfiguration.ELASTICSEARCH_PORT + " should be specified together");
    }

    @Test
    void validateHostsConfigurationOptionsShouldThrowWhenMonoHostWithoutAddress() {
        assertThatThrownBy(() ->
        ElasticSearchConfiguration.validateHostsConfigurationOptions(
            Optional.empty(),
            Optional.of(9200),
            ImmutableList.of()))
        .isInstanceOf(ConfigurationException.class)
        .hasMessage(ElasticSearchConfiguration.ELASTICSEARCH_MASTER_HOST + " and " +
            ElasticSearchConfiguration.ELASTICSEARCH_PORT + " should be specified together");
    }

    @Test
    void validateHostsConfigurationOptionsShouldAcceptMonoHostConfiguration() throws Exception {
        ElasticSearchConfiguration.validateHostsConfigurationOptions(
            Optional.of("localhost"),
            Optional.of(9200),
            ImmutableList.of());
    }

    @Test
    void validateHostsConfigurationOptionsShouldAcceptMultiHostConfiguration() throws Exception {
        ElasticSearchConfiguration.validateHostsConfigurationOptions(
            Optional.empty(),
            Optional.empty(),
            ImmutableList.of("localhost:9200"));
    }

    @Test
    void nbReplicaShouldThrowWhenNegative() {
        assertThatThrownBy(() ->
                ElasticSearchConfiguration.builder()
                        .nbReplica(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void waitForActiveShardsShouldThrowWhenNegative() {
        assertThatThrownBy(() ->
            ElasticSearchConfiguration.builder()
                .waitForActiveShards(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nbShardsShouldThrowWhenNegative() {
        assertThatThrownBy(() ->
                ElasticSearchConfiguration.builder()
                        .nbShards(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nbShardsShouldThrowWhenZero() {
        assertThatThrownBy(() ->
                ElasticSearchConfiguration.builder()
                        .nbShards(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCredentialShouldReturnConfiguredValue() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        String user = "johndoe";
        String password = "secret";
        configuration.addProperty("elasticsearch.user", user);
        configuration.addProperty("elasticsearch.password", password);

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.credential())
            .contains(Credential.of(user, password));
    }

    @Test
    void getCredentialShouldReturnEmptyWhenNotConfigured() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.credential())
            .isEmpty();
    }

    @Test
    void fromPropertiesShouldThrowWhenOnlyUsername() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        configuration.addProperty("elasticsearch.user", "username");

        assertThatThrownBy(() -> ElasticSearchConfiguration.fromProperties(configuration))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("password cannot be null when username is specified");
    }

    @Test
    void fromPropertiesShouldThrowWhenOnlyPassword() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        configuration.addProperty("elasticsearch.password", "password");

        assertThatThrownBy(() -> ElasticSearchConfiguration.fromProperties(configuration))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("username cannot be null when password is specified");
    }

    @Test
    void getHostSchemeShouldReturnConfiguredValue() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        configuration.addProperty("elasticsearch.hostScheme", "https");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.hostScheme())
            .isEqualTo(HostScheme.HTTPS);
    }

    @Test
    void getHostSchemeShouldBeCaseInsensitive() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        configuration.addProperty("elasticsearch.hostScheme", "HTTPs");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.hostScheme())
            .isEqualTo(HostScheme.HTTPS);
    }

    @Test
    void getHostSchemeShouldReturnHttpWhenNotConfigured() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        ElasticSearchConfiguration elasticSearchConfiguration = ElasticSearchConfiguration.fromProperties(configuration);

        assertThat(elasticSearchConfiguration.hostScheme())
            .isEqualTo(HostScheme.HTTP);
    }

    @Test
    void fromPropertiesShouldThrowWhenInvalidValue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("elasticsearch.hosts", "127.0.0.1");

        configuration.addProperty("elasticsearch.hostScheme", "invalid-protocol");

        assertThatThrownBy(() -> ElasticSearchConfiguration.fromProperties(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unknown HostScheme 'invalid-protocol'");
    }
}
