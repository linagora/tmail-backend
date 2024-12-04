package com.linagora.tmail;

import static com.rabbitmq.client.ConnectionFactory.DEFAULT_VHOST;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.util.DurationParser;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;

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
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ScheduledReconnectionHandler implements Startable {
    public record ScheduledReconnectionHandlerConfiguration(boolean enabled, Duration interval) {
        public static final boolean ENABLED = true;
        public static final Duration ONE_MINUTE = Duration.ofSeconds(60);

        public static ScheduledReconnectionHandlerConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
            try {
                Configuration configuration = propertiesProvider.getConfiguration("rabbitmq");
                boolean enabled = configuration.getBoolean("scheduled.consumer.reconnection.enabled", ENABLED);
                Duration interval = Optional.ofNullable(configuration.getString("scheduled.consumer.reconnection.interval", null))
                    .map(s -> DurationParser.parse(s, ChronoUnit.SECONDS))
                    .orElse(ONE_MINUTE);

                return new ScheduledReconnectionHandlerConfiguration(enabled, interval);
            } catch (FileNotFoundException e) {
                return new ScheduledReconnectionHandlerConfiguration(false, ONE_MINUTE);
            }
        }
    }

    public static class Module extends AbstractModule {
        @Provides
        ScheduledReconnectionHandlerConfiguration configuration(PropertiesProvider propertiesProvider) throws ConfigurationException {
            return ScheduledReconnectionHandlerConfiguration.parse(propertiesProvider);
        }

        @ProvidesIntoSet
        InitializationOperation start(ScheduledReconnectionHandler scheduledReconnectionHandler) {
            return InitilizationOperationBuilder
                .forClass(ScheduledReconnectionHandler.class)
                .init(scheduledReconnectionHandler::start);
        }
    }

    public interface RabbitMQManagementAPI {
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

        ErrorDecoder RETRY_500 = (methodKey, response) -> {
            if (response.status() == 500) {
                throw new RetryableException(response.status(), "Error encountered, scheduling retry",
                    response.request().httpMethod(), new Date(), response.request());
            }
            throw new RuntimeException("Non recoverable exception status: " + response.status());
        };

        @RequestLine(value = "GET /api/queues/{vhost}/{name}", decodeSlash = false)
        MessageQueueDetails queueDetails(@Param("vhost") String vhost, @Param("name") String name);

    }

    private static final ImmutableList<String> QUEUES_TO_MONITOR = ImmutableList.of("JamesMailQueue-workqueue-spool",
        "JamesMailQueue-workqueue-outgoing",
        "mailboxEvent-workQueue-org.apache.james.events.GroupRegistrationHandler$GroupRegistrationHandlerGroup",
        "jmapEvent-workQueue-org.apache.james.events.GroupRegistrationHandler$GroupRegistrationHandlerGroup");
    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledReconnectionHandler.class);
    
    private final Set<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlers;
    private final RabbitMQManagementAPI mqManagementAPI;
    private final RabbitMQConfiguration configuration;
    private final SimpleConnectionPool connectionPool;
    private final ScheduledReconnectionHandlerConfiguration scheduledReconnectionHandlerConfiguration;
    private Disposable disposable;

    @Inject
    public ScheduledReconnectionHandler(Set<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlers,
                                        RabbitMQConfiguration configuration,
                                        SimpleConnectionPool connectionPool,
                                        ScheduledReconnectionHandlerConfiguration scheduledReconnectionHandlerConfiguration) {
        this.reconnectionHandlers = reconnectionHandlers;
        this.mqManagementAPI = RabbitMQManagementAPI.from(configuration);
        this.configuration = configuration;
        this.connectionPool = connectionPool;
        this.scheduledReconnectionHandlerConfiguration = scheduledReconnectionHandlerConfiguration;
    }
    
    public void start() {
        if (scheduledReconnectionHandlerConfiguration.enabled()) {
            disposable = Flux.interval(Duration.ofMinutes(1))
                .filter(any -> restartNeeded())
                .concatMap(any -> restart())
                .onErrorResume(e -> {
                    LOGGER.warn("Failed to run scheduled RabbitMQ consumer checks", e);
                    return Mono.empty();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        }
    }

    @PreDestroy
    public void stop() {
        Optional.ofNullable(disposable).ifPresent(Disposable::dispose);
    }
    
    private Mono<Void> restart() {
        LOGGER.warn("One of the queues has no consumer thus restarting all consumers");
        return connectionPool.getResilientConnection()
            .flatMap(connection -> Flux.fromIterable(reconnectionHandlers)
                .concatMap(h -> h.handleReconnection(connection))
                .then());
    }
    
    private boolean restartNeeded() {
        return QUEUES_TO_MONITOR.stream()
            .filter(this::hasNoConsumers)
            .findAny()
            .isPresent();
    }

    private boolean hasNoConsumers(String queue) {
        return mqManagementAPI.queueDetails(configuration.getVhost().orElse(DEFAULT_VHOST), queue)
            .getConsumerDetails()
            .isEmpty();
    }
}
