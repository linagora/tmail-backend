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

package com.linagora.tmail.saas.rabbitmq.subscription;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;

public class SaaSDomainSubscriptionConsumerTest {
    private static final String EXCHANGE_NAME = SaaSSubscriptionRabbitMQConfiguration.TWP_SAAS_SUBSCRIPTION_EXCHANGE_DEFAULT;
    private static final String ROUTING_KEY = SaaSSubscriptionRabbitMQConfiguration.TWP_SAAS_DOMAIN_SUBSCRIPTION_ROUTING_KEY_DEFAULT;
    private static final String DOMAIN_ROUTING_KEY = SaaSSubscriptionRabbitMQConfiguration.TWP_SAAS_DOMAIN_SUBSCRIPTION_ROUTING_KEY_DEFAULT;
    private static final Domain DOMAIN = Domain.of("twake.app");

    private final ConditionFactory await = Awaitility.with()
        .pollInterval(Duration.ofMillis(500))
        .and()
        .with()
        .pollDelay(Duration.ofMillis(500))
        .await()
        .atMost(10, TimeUnit.SECONDS);

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    private DomainList domainList;
    private SaaSDomainSubscriptionConsumer testee;

    @BeforeEach
    void setUp() throws URISyntaxException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(rabbitMQExtension.getRabbitMQ().amqpUri())
            .managementUri(rabbitMQExtension.getRabbitMQ().managementUri())
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        SaaSSubscriptionRabbitMQConfiguration saasSubscriptionRabbitMQConfiguration =
            new SaaSSubscriptionRabbitMQConfiguration(EXCHANGE_NAME, ROUTING_KEY, DOMAIN_ROUTING_KEY);

        TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration = new TWPCommonRabbitMQConfiguration(
            Optional.empty(),
            Optional.empty(),
            false);

        domainList = new SimpleDomainList();

        testee = new SaaSDomainSubscriptionConsumer(
            rabbitMQExtension.getRabbitChannelPool(),
            rabbitMQConfiguration,
            twpCommonRabbitMQConfiguration,
            saasSubscriptionRabbitMQConfiguration,
            domainList);
        testee.init();
    }

    @AfterEach
    void tearDown() {
        testee.close();
    }

    @Test
    void shouldRegisterSaasValidatedDomain() {
        String validMessage = String.format("""
            {
                "domain": "%s",
                "validated": true,
                "features": {
                    "mail": {
                        "storageQuota": 1234,
                        "mailsSentPerMinute": 10,
                        "mailsSentPerHour": 100,
                        "mailsSentPerDay": 1000,
                        "mailsReceivedPerMinute": 20,
                        "mailsReceivedPerHour": 200,
                        "mailsReceivedPerDay": 2000
                    }
                }
            }
            """, DOMAIN.asString());

        publishAmqpSaaSDomainSubscriptionMessage(validMessage);

        await.untilAsserted(() -> assertThat(domainList.containsDomain(DOMAIN)).isTrue());
    }

    @Test
    void shouldIgnoreSaasUnvalidatedDomain() {
        Domain unvalidatedDomain = Domain.of("unvalidated.org");
        String unvalidatedMessage = String.format("""
            {
                "domain": "%s",
                "validated": false,
                "features": {
                    "mail": {
                        "storageQuota": 1234,
                        "mailsSentPerMinute": 10,
                        "mailsSentPerHour": 100,
                        "mailsSentPerDay": 1000,
                        "mailsReceivedPerMinute": 20,
                        "mailsReceivedPerHour": 200,
                        "mailsReceivedPerDay": 2000
                    }
                }
            }
            """, unvalidatedDomain.asString());

        publishAmqpSaaSDomainSubscriptionMessage(unvalidatedMessage);

        String validMessage = String.format("""
            {
                "domain": "%s",
                "validated": true,
                "features": {
                    "mail": {
                        "storageQuota": 1234,
                        "mailsSentPerMinute": 10,
                        "mailsSentPerHour": 100,
                        "mailsSentPerDay": 1000,
                        "mailsReceivedPerMinute": 20,
                        "mailsReceivedPerHour": 200,
                        "mailsReceivedPerDay": 2000
                    }
                }
            }
            """, DOMAIN.asString());

        publishAmqpSaaSDomainSubscriptionMessage(validMessage);

        await.untilAsserted(() -> {
            assertThat(domainList.containsDomain(DOMAIN)).isTrue();
            assertThat(domainList.containsDomain(unvalidatedDomain)).isFalse();
        });
    }

    @Test
    void shouldBeIdempotent() {
        String validMessage = String.format("""
            {
                "domain": "%s",
                "validated": true,
                "features": {
                    "mail": {
                        "storageQuota": 1234,
                        "mailsSentPerMinute": 10,
                        "mailsSentPerHour": 100,
                        "mailsSentPerDay": 1000,
                        "mailsReceivedPerMinute": 20,
                        "mailsReceivedPerHour": 200,
                        "mailsReceivedPerDay": 2000
                    }
                }
            }
            """, DOMAIN.asString());

        publishAmqpSaaSDomainSubscriptionMessage(validMessage);
        publishAmqpSaaSDomainSubscriptionMessage(validMessage);

        await.untilAsserted(() -> assertThat(domainList.containsDomain(DOMAIN)).isTrue());
    }

    @Test
    void invalidAmqpMessageShouldNotCrashConsumer() {
        String invalidMessage = "{ invalid json }";
        publishAmqpSaaSDomainSubscriptionMessage(invalidMessage);

        // Publish a valid message after invalid one to ensure consumer is still alive
        String validMessage = String.format("""
            {
                "domain": "%s",
                "validated": true,
                "features": {
                    "mail": {
                        "storageQuota": 1234,
                        "mailsSentPerMinute": 10,
                        "mailsSentPerHour": 100,
                        "mailsSentPerDay": 1000,
                        "mailsReceivedPerMinute": 20,
                        "mailsReceivedPerHour": 200,
                        "mailsReceivedPerDay": 2000
                    }
                }
            }
            """, DOMAIN.asString());
        publishAmqpSaaSDomainSubscriptionMessage(validMessage);

        await.untilAsserted(() -> assertThat(domainList.containsDomain(DOMAIN)).isTrue());
    }

    private void publishAmqpSaaSDomainSubscriptionMessage(String message) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(
                EXCHANGE_NAME,
                DOMAIN_ROUTING_KEY,
                message.getBytes(UTF_8))))
            .block();
    }
}
