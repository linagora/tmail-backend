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

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.LocalDateTime;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.james.backends.es.ElasticSearchConfiguration.HostScheme;
import org.apache.james.backends.es.ElasticSearchConfiguration.SSLConfiguration.HostNameVerifier;
import org.apache.james.backends.es.ElasticSearchConfiguration.SSLConfiguration.SSLTrustStore;
import org.apache.james.backends.es.ElasticSearchConfiguration.SSLConfiguration.SSLValidationStrategy;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class ClientProvider implements Provider<ReactorElasticSearchClient> {

    private static class HttpAsyncClientConfigurer {

        private static final TrustStrategy TRUST_ALL = (x509Certificates, authType) -> true;
        private static final HostnameVerifier ACCEPT_ANY_HOSTNAME = (hostname, sslSession) -> true;

        private final ElasticSearchConfiguration configuration;

        private HttpAsyncClientConfigurer(ElasticSearchConfiguration configuration) {
            this.configuration = configuration;
        }

        private HttpAsyncClientBuilder configure(HttpAsyncClientBuilder builder) {
            configureAuthentication(builder);
            configureHostScheme(builder);

            return builder;
        }

        private void configureHostScheme(HttpAsyncClientBuilder builder) {
            HostScheme scheme = configuration.hostScheme();

            switch (scheme) {
                case HTTP:
                    return;
                case HTTPS:
                    configureSSLOptions(builder);
                    return;
                default:
                    throw new NotImplementedException(
                        String.format("unrecognized hostScheme '%s'", scheme.name()));
            }
        }

        private void configureSSLOptions(HttpAsyncClientBuilder builder) {
            try {
                builder
                    .setSSLContext(sslContext())
                    .setSSLHostnameVerifier(hostnameVerifier());
            } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException | CertificateException | IOException e) {
                throw new RuntimeException("Cannot set SSL options to the builder", e);
            }
        }

        private SSLContext sslContext() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException,
            CertificateException, IOException {

            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();

            SSLValidationStrategy strategy = configuration.sslConfiguration()
                .getStrategy();

            return switch (strategy) {
                case DEFAULT -> sslContextBuilder.build();
                case IGNORE -> sslContextBuilder.loadTrustMaterial(TRUST_ALL)
                    .build();
                case OVERRIDE -> applyTrustStore(sslContextBuilder)
                    .build();
            };
        }

        private HostnameVerifier hostnameVerifier() {
            HostNameVerifier hostnameVerifier = configuration.sslConfiguration()
                .getHostNameVerifier();

            return switch (hostnameVerifier) {
                case DEFAULT -> new DefaultHostnameVerifier();
                case ACCEPT_ANY_HOSTNAME -> ACCEPT_ANY_HOSTNAME;
            };
        }

        private SSLContextBuilder applyTrustStore(SSLContextBuilder sslContextBuilder) throws CertificateException, NoSuchAlgorithmException,
            KeyStoreException, IOException {

            SSLTrustStore trustStore = configuration.sslConfiguration()
                .getTrustStore()
                .orElseThrow(() -> new IllegalStateException("SSLTrustStore cannot to be empty"));

            return sslContextBuilder
                .loadTrustMaterial(trustStore.getFile(), trustStore.getPassword());
        }

        private void configureAuthentication(HttpAsyncClientBuilder builder) {
            configuration.credential()
                .ifPresent(credential -> {
                    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    credentialsProvider.setCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials(credential.username(), String.valueOf(credential.password())));
                    builder.setDefaultCredentialsProvider(credentialsProvider);
                });
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientProvider.class);

    private final ElasticSearchConfiguration configuration;
    private final RestHighLevelClient elasticSearchRestHighLevelClient;
    private final HttpAsyncClientConfigurer httpAsyncClientConfigurer;
    private final ReactorElasticSearchClient client;

    @Inject
    @VisibleForTesting
    ClientProvider(ElasticSearchConfiguration configuration) {
        this.httpAsyncClientConfigurer = new HttpAsyncClientConfigurer(configuration);
        this.configuration = configuration;
        this.elasticSearchRestHighLevelClient = connect(configuration);
        this.client = new ReactorElasticSearchClient(this.elasticSearchRestHighLevelClient);
    }

    private RestHighLevelClient connect(ElasticSearchConfiguration configuration) {
        Duration waitDelay = Duration.ofMillis(configuration.minDelay());
        boolean suppressLeadingZeroElements = true;
        boolean suppressTrailingZeroElements = true;
        return Mono.fromCallable(() -> connectToCluster(configuration))
            .doOnError(e -> LOGGER.warn("Error establishing ElasticSearch connection. Next retry scheduled in {}",
                DurationFormatUtils.formatDurationWords(waitDelay.toMillis(), suppressLeadingZeroElements, suppressTrailingZeroElements), e))
            .retryWhen(Retry.backoff(configuration.maxRetries(), waitDelay).scheduler(Schedulers.boundedElastic()))
            .block();
    }

    private RestHighLevelClient connectToCluster(ElasticSearchConfiguration configuration) {
        LOGGER.info("Trying to connect to ElasticSearch service at {}", LocalDateTime.now());

        return new RestHighLevelClient(
            RestClient
                .builder(hostsToHttpHosts())
                .setHttpClientConfigCallback(httpAsyncClientConfigurer::configure)
                .setMaxRetryTimeoutMillis(Math.toIntExact(configuration.requestTimeout().toMillis())));
    }

    private HttpHost[] hostsToHttpHosts() {
        return configuration.hosts().stream()
            .map(host -> new HttpHost(host.getHostName(), host.getPort(), configuration.hostScheme().name()))
            .toArray(HttpHost[]::new);
    }

    @Override
    public ReactorElasticSearchClient get() {
        return client;
    }

    @PreDestroy
    public void close() throws IOException {
        elasticSearchRestHighLevelClient.close();
    }
}
