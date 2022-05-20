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

package org.apache.james.modules.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.time.Duration;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.james.mailbox.model.ContentType.MimeType;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

class TikaConfigurationReaderTest {
    @Test
    void readTikaConfigurationShouldAcceptMandatoryValues() throws Exception {
        PropertiesConfiguration configuration = newConfiguration();
        configuration.read(new StringReader(
            """
                tika.enabled=true
                tika.host=172.0.0.5
                tika.port=889
                tika.timeoutInMillis=500
                """));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .enabled()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(500)
                    .cacheDisabled()
                    .cacheWeightInBytes(100L * 1024L * 1024L)
                    .cacheEvictionPeriod(Duration.ofDays(1))
                    .build());
    }

    @Test
    void readTikaConfigurationShouldReturnDefaultOnMissingHost() throws Exception {
        PropertiesConfiguration configuration = newConfiguration();
        configuration.read(new StringReader(
            """
                tika.enabled=true
                tika.port=889
                tika.timeoutInMillis=500
                """));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .enabled()
                    .host("127.0.0.1")
                    .port(889)
                    .timeoutInMillis(500)
                    .build());
    }

    @Test
    void readTikaConfigurationShouldReturnDefaultOnMissingPort() throws Exception {
        PropertiesConfiguration configuration = newConfiguration();
        configuration.read(new StringReader(
            """
                tika.enabled=true
                tika.host=172.0.0.5
                tika.timeoutInMillis=500
                """));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .enabled()
                    .host("172.0.0.5")
                    .port(9998)
                    .timeoutInMillis(500)
                    .build());
    }

    @Test
    void readTikaConfigurationShouldReturnDefaultOnMissingTimeout() throws Exception {
        PropertiesConfiguration configuration = newConfiguration();
        configuration.read(new StringReader(
            "tika.enabled=true\n" +
            "tika.host=172.0.0.5\n" +
            "tika.port=889\n"));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .enabled()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(30 * 1000)
                    .build());
    }

    @Test
    void tikaShouldBeDisabledByDefault() throws Exception {
        PropertiesConfiguration configuration = newConfiguration();
        configuration.read(new StringReader(""));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .disabled()
                    .build());
    }

    @Test
    void readTikaConfigurationShouldParseUnitForCacheEvictionPeriod() throws Exception {
        PropertiesConfiguration configuration = newConfiguration();
        configuration.read(new StringReader(
            """
                tika.enabled=true
                tika.host=172.0.0.5
                tika.port=889
                tika.timeoutInMillis=500
                tika.cache.eviction.period=2H"""));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .enabled()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(500)
                    .cacheEvictionPeriod(Duration.ofHours(2))
                    .build());
    }

    @Test
    void readTikaConfigurationShouldDefaultToSecondWhenMissingUnitForCacheEvitionPeriod() throws Exception {
        PropertiesConfiguration configuration = newConfiguration();
        configuration.read(new StringReader(
            """
                tika.enabled=true
                tika.host=172.0.0.5
                tika.port=889
                tika.timeoutInMillis=500
                tika.cache.eviction.period=3600"""));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .enabled()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(500)
                    .cacheEvictionPeriod(Duration.ofHours(1))
                    .build());
    }

    @Test
    void readTikaConfigurationShouldParseUnitForCacheWeightMax() throws Exception {
        PropertiesConfiguration configuration = newConfiguration();
        configuration.read(new StringReader(
            """
                tika.enabled=true
                tika.host=172.0.0.5
                tika.port=889
                tika.timeoutInMillis=500
                tika.cache.weight.max=200M"""));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .enabled()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(500)
                    .cacheWeightInBytes(200L * 1024L * 1024L)
                    .build());
    }

    @Test
    void readTikaConfigurationShouldDefaultToByteAsSizeUnit() throws Exception {
        PropertiesConfiguration configuration = newConfiguration();
        configuration.read(new StringReader(
            """
                tika.enabled=true
                tika.host=172.0.0.5
                tika.port=889
                tika.timeoutInMillis=500
                tika.cache.weight.max=1520000"""));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .enabled()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(500)
                    .cacheWeightInBytes(1520000)
                    .build());
    }

    @Test
    void readTikaConfigurationShouldEnableCacheWhenConfigured() throws Exception {
        PropertiesConfiguration configuration = newConfiguration();
        configuration.read(new StringReader(
            """
                tika.enabled=true
                tika.cache.enabled=true
                tika.host=172.0.0.5
                tika.port=889
                tika.timeoutInMillis=500
                tika.cache.weight.max=1520000"""));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .enabled()
                    .cacheEnabled()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(500)
                    .cacheWeightInBytes(1520000)
                    .build());
    }

    @Test
    void readTikaConfigurationShouldNotHaveContentTypeBlacklist() throws Exception {
        PropertiesConfiguration configuration = newConfiguration();
        configuration.read(new StringReader(
            """
                tika.enabled=true
                tika.cache.enabled=true
                tika.host=172.0.0.5
                tika.port=889
                tika.timeoutInMillis=500
                tika.cache.weight.max=1520000"""));
        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .enabled()
                    .cacheEnabled()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(500)
                    .cacheWeightInBytes(1520000)
                    .contentTypeBlacklist(ImmutableSet.of())
                    .build());
    }

    @Test
    void readTikaConfigurationShouldHaveContentTypeBlacklist() throws Exception {
        PropertiesConfiguration configuration = newConfiguration();
        configuration.read(new StringReader(
            """
                tika.enabled=true
                tika.cache.enabled=true
                tika.host=172.0.0.5
                tika.port=889
                tika.timeoutInMillis=500
                tika.cache.weight.max=1520000
                tika.contentType.blacklist=application/ics,application/zip"""));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .enabled()
                    .cacheEnabled()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(500)
                    .cacheWeightInBytes(1520000)
                    .contentTypeBlacklist(ImmutableSet.of(MimeType.of("application/ics"), MimeType.of("application/zip")))
                    .build());
    }

    @Test
    void readTikaConfigurationShouldHaveContentTypeBlacklistWithWhiteSpace() throws Exception {
        PropertiesConfiguration configuration = newConfiguration();
        configuration.read(new StringReader(
            """
                tika.enabled=true
                tika.cache.enabled=true
                tika.host=172.0.0.5
                tika.port=889
                tika.timeoutInMillis=500
                tika.cache.weight.max=1520000
                tika.contentType.blacklist=application/ics, application/zip"""));

        assertThat(TikaConfigurationReader.readTikaConfiguration(configuration))
            .isEqualTo(
                TikaConfiguration.builder()
                    .enabled()
                    .cacheEnabled()
                    .host("172.0.0.5")
                    .port(889)
                    .timeoutInMillis(500)
                    .cacheWeightInBytes(1520000)
                    .contentTypeBlacklist(ImmutableSet.of(MimeType.of("application/ics"), MimeType.of("application/zip")))
                    .build());
    }

    private PropertiesConfiguration newConfiguration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.setListDelimiterHandler(new DefaultListDelimiterHandler(','));
        return configuration;
    }
}