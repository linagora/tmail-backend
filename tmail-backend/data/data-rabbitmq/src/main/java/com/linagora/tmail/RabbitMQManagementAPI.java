/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 *******************************************************************/

package com.linagora.tmail;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;

import com.fasterxml.jackson.annotation.JsonProperty;

import feign.Client;
import feign.Feign;
import feign.Param;
import feign.RequestLine;
import feign.RetryableException;
import feign.Retryer;
import feign.auth.BasicAuthRequestInterceptor;
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;

public interface RabbitMQManagementAPI {
    int NOT_FOUND_404 = 404;
    int INTERNAL_SERVER_ERROR_500 = 500;

    class MessageQueueDetails {
        @JsonProperty("name")
        String name;

        @JsonProperty("vhost")
        String vhost;

        @JsonProperty("auto_delete")
        boolean autoDelete;

        @JsonProperty("durable")
        boolean durable;

        @JsonProperty("exclusive")
        boolean exclusive;

        @JsonProperty("arguments")
        Map<String, String> arguments;

        @JsonProperty("consumer_details")
        List<ConsumerDetails> consumerDetails;

        @JsonProperty("messages")
        long queueLength;

        public String getName() {
            return name;
        }

        public String getVhost() {
            return vhost;
        }

        public boolean isAutoDelete() {
            return autoDelete;
        }

        public boolean isDurable() {
            return durable;
        }

        public boolean isExclusive() {
            return exclusive;
        }

        public Map<String, String> getArguments() {
            return arguments;
        }

        public List<ConsumerDetails> getConsumerDetails() {
            return consumerDetails;
        }

        public long getQueueLength() {
            return queueLength;
        }
    }

    class ConsumerDetails {
        @JsonProperty("consumer_tag")
        String tag;

        @JsonProperty("activity_status")
        String status;

        public String getStatus() {
            return this.status;
        }

        public String getTag() {
            return this.tag;
        }
    }

    static RabbitMQManagementAPI from(RabbitMQConfiguration configuration) {
        try {
            RabbitMQConfiguration.ManagementCredentials credentials =
                configuration.getManagementCredentials();
            RabbitMQManagementAPI rabbitMQManagementAPI = Feign.builder()
                .client(getClient(configuration))
                .requestInterceptor(new BasicAuthRequestInterceptor(credentials.getUser(),
                    new String(credentials.getPassword())))
                .logger(new Slf4jLogger(org.apache.james.backends.rabbitmq.RabbitMQManagementAPI.class))
                .logLevel(feign.Logger.Level.FULL)
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .retryer(new Retryer.Default())
                .errorDecoder(RETRY_500)
                .target(RabbitMQManagementAPI.class, configuration.getManagementUri().toString());

            return rabbitMQManagementAPI;
        } catch (KeyManagementException | NoSuchAlgorithmException | CertificateException | KeyStoreException |
                 IOException | UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private static Client getClient(RabbitMQConfiguration configuration)
        throws KeyManagementException, NoSuchAlgorithmException, CertificateException,
        KeyStoreException, IOException, UnrecoverableKeyException {
        if (configuration.useSslManagement()) {
            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();

            setupSslValidationStrategy(sslContextBuilder, configuration);

            setupClientCertificateAuthentication(sslContextBuilder, configuration);

            SSLContext sslContext = sslContextBuilder.build();

            return new Client.Default(sslContext.getSocketFactory(),
                getHostNameVerifier(configuration));
        } else {
            return new Client.Default(null, null);
        }

    }

    private static HostnameVerifier getHostNameVerifier(RabbitMQConfiguration configuration) {
        switch (configuration.getSslConfiguration().getHostNameVerifier()) {
            case ACCEPT_ANY_HOSTNAME:
                return ((hostname, session) -> true);
            default:
                return new DefaultHostnameVerifier();
        }
    }

    private static void setupClientCertificateAuthentication(SSLContextBuilder sslContextBuilder,
                                                             RabbitMQConfiguration configuration)
        throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException,
        CertificateException, IOException {
        Optional<RabbitMQConfiguration.SSLConfiguration.SSLKeyStore> keyStore =
            configuration.getSslConfiguration().getKeyStore();

        if (keyStore.isPresent()) {
            RabbitMQConfiguration.SSLConfiguration.SSLKeyStore sslKeyStore = keyStore.get();

            sslContextBuilder.loadKeyMaterial(sslKeyStore.getFile(), sslKeyStore.getPassword(),
                sslKeyStore.getPassword());
        }
    }

    private static void setupSslValidationStrategy(SSLContextBuilder sslContextBuilder,
                                                   RabbitMQConfiguration configuration)
        throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        RabbitMQConfiguration.SSLConfiguration.SSLValidationStrategy strategy = configuration
            .getSslConfiguration()
            .getStrategy();

        final TrustStrategy trustAll = (x509Certificates, authType) -> true;

        switch (strategy) {
            case DEFAULT:
                break;
            case IGNORE:
                sslContextBuilder.loadTrustMaterial(trustAll);
                break;
            case OVERRIDE:
                applyTrustStore(sslContextBuilder, configuration);
                break;
            default:
                throw new NotImplementedException(
                    String.format("unrecognized strategy '%s'", strategy.name()));
        }
    }

    private static SSLContextBuilder applyTrustStore(SSLContextBuilder sslContextBuilder,
                                                     RabbitMQConfiguration configuration)
        throws CertificateException, NoSuchAlgorithmException,
        KeyStoreException, IOException {

        RabbitMQConfiguration.SSLConfiguration.SSLTrustStore trustStore =
            configuration.getSslConfiguration()
                .getTrustStore()
                .orElseThrow(() -> new IllegalStateException("SSLTrustStore cannot to be empty"));

        return sslContextBuilder
            .loadTrustMaterial(trustStore.getFile(), trustStore.getPassword());
    }

    class QueueNotFoundException extends RuntimeException {

    }

    ErrorDecoder RETRY_500 = (methodKey, response) -> {
        switch (response.status()) {
            case NOT_FOUND_404:
                throw new QueueNotFoundException();
            case INTERNAL_SERVER_ERROR_500:
                throw new RetryableException(response.status(), "Error encountered, scheduling retry",
                    response.request().httpMethod(), new Date(), response.request());
            default:
                throw new RuntimeException("Non-recoverable exception status: " + response.status());
        }
    };

    @RequestLine(value = "GET /api/queues/{vhost}/{name}", decodeSlash = false)
    MessageQueueDetails queueDetails(@Param("vhost") String vhost, @Param("name") String name);

}
