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

import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.MAILS_RECEIVED_PER_DAYS_UNLIMITED;
import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.MAILS_RECEIVED_PER_HOURS_UNLIMITED;
import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.MAILS_RECEIVED_PER_MINUTE_UNLIMITED;
import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.MAILS_SENT_PER_DAYS_UNLIMITED;
import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.MAILS_SENT_PER_HOURS_UNLIMITED;
import static com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.MAILS_SENT_PER_MINUTE_UNLIMITED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;

public interface SaaSDomainSubscriptionConsumerContract {
    Domain DOMAIN = Domain.of("twake.app");
    RateLimitingDefinition RATE_LIMITING_1 = RateLimitingDefinition.builder()
        .mailsSentPerMinute(10L)
        .mailsSentPerHours(100L)
        .mailsSentPerDays(1000L)
        .mailsReceivedPerMinute(20L)
        .mailsReceivedPerHours(200L)
        .mailsReceivedPerDays(2000L)
        .build();
    RateLimitingDefinition UNLIMITED = RateLimitingDefinition.builder()
        .mailsSentPerMinute(MAILS_SENT_PER_MINUTE_UNLIMITED)
        .mailsSentPerHours(MAILS_SENT_PER_HOURS_UNLIMITED)
        .mailsSentPerDays(MAILS_SENT_PER_DAYS_UNLIMITED)
        .mailsReceivedPerMinute(MAILS_RECEIVED_PER_MINUTE_UNLIMITED)
        .mailsReceivedPerHours(MAILS_RECEIVED_PER_HOURS_UNLIMITED)
        .mailsReceivedPerDays(MAILS_RECEIVED_PER_DAYS_UNLIMITED)
        .build();

    ConditionFactory await = Awaitility.with()
        .pollInterval(Duration.ofMillis(500))
        .and()
        .with()
        .pollDelay(Duration.ofMillis(500))
        .await()
        .atMost(10, TimeUnit.SECONDS);

    @RegisterExtension
    RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    DomainList domainList();
    MaxQuotaManager maxQuotaManager();
    RateLimitingRepository rateLimitingRepository();
    SaaSDomainSubscriptionConsumer testee();

    @AfterEach
    default void tearDown() {
        testee().close();
    }

    @Test
    default void shouldRegisterSaasValidatedDomain() {
        String validMessage = String.format("""
        {
            "domain": "%s",
            "mailDnsConfigurationValidated": true,
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

        await.untilAsserted(() -> assertThat(domainList().containsDomain(DOMAIN)).isTrue());
    }

    @Test
    default void shouldRegisterSaasValidatedDomainOnConfigurationExchange() {
        String validMessage = String.format("""
        {
            "domain": "%s",
            "mailDnsConfigurationValidated": true
        }
        """, DOMAIN.asString());

        publishAmqpSaaSConfigurationMessage(validMessage);

        await.untilAsserted(() -> assertThat(domainList().containsDomain(DOMAIN)).isTrue());
    }

    @Test
    default void shouldNotRegisterSaasValidatedDomainWhenValidatedNoop() {
        String validMessage = String.format("""
        {
            "domain": "%s",
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

        await.untilAsserted(() -> assertThat(domainList().containsDomain(DOMAIN)).isFalse());
    }

    @Test
    default void shouldNotUnRegisterSaasValidatedDomainWhenValidatedNoop() {
        publishAmqpSaaSDomainSubscriptionMessage(String.format("""
        {
            "domain": "%s",
            "mailDnsConfigurationValidated": true,
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
        """, DOMAIN.asString()));

        publishAmqpSaaSDomainSubscriptionMessage(String.format("""
        {
            "domain": "%s",
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
        """, DOMAIN.asString()));

        await.untilAsserted(() -> assertThat(domainList().containsDomain(DOMAIN)).isTrue());
    }

    @Test
    default void shouldIgnoreSaasUnvalidatedDomain() {
        Domain unvalidatedDomain = Domain.of("unvalidated.org");
        String unvalidatedMessage = String.format("""
        {
            "domain": "%s",
            "mailDnsConfigurationValidated": false,
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
            "mailDnsConfigurationValidated": true,
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
            assertThat(domainList().containsDomain(DOMAIN)).isTrue();
            assertThat(domainList().containsDomain(unvalidatedDomain)).isFalse();
        });
    }

    @Test
    default void shouldSetStorageQuotaWhenDomainHasNoQuotaYet() {
        String validMessage = String.format("""
        {
            "domain": "%s",
            "mailDnsConfigurationValidated": true,
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

        await.untilAsserted(() -> assertThat(maxQuotaManager().getDomainMaxStorage(DOMAIN))
            .isEqualTo(Optional.of(QuotaSizeLimit.size(1234))));
    }

    @Test
    default void shouldSetStorageQuotaWhenDomainHasNoQuotaYetWhenValidatedNoop() {
        String validMessage = String.format("""
        {
            "domain": "%s",
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

        await.untilAsserted(() -> assertThat(maxQuotaManager().getDomainMaxStorage(DOMAIN))
            .isEqualTo(Optional.of(QuotaSizeLimit.size(1234))));
    }

    @Test
    default void shouldSetRateLimitingWhenDomainHasNoRateLimitingYet() {
        String validMessage = String.format("""
        {
            "domain": "%s",
            "mailDnsConfigurationValidated": true,
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
            RateLimitingDefinition rateLimitingDefinition = Mono.from(rateLimitingRepository().getRateLimiting(DOMAIN)).block();
            assertThat(rateLimitingDefinition).isEqualTo(RATE_LIMITING_1);
        });
    }

    @Test
    default void shouldSetRateLimitingWhenDomainHasNoRateLimitingYetWhenValidatedNoop() {
        String validMessage = String.format("""
        {
            "domain": "%s",
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
            RateLimitingDefinition rateLimitingDefinition = Mono.from(rateLimitingRepository().getRateLimiting(DOMAIN)).block();
            assertThat(rateLimitingDefinition).isEqualTo(RATE_LIMITING_1);
        });
    }

    @Test
    default void shouldSupportSetUnlimitedStorageQuotaForDomain() {
        String validMessage = String.format("""
        {
            "domain": "%s",
            "mailDnsConfigurationValidated": true,
            "features": {
                "mail": {
                    "storageQuota": -1,
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

        await.untilAsserted(() -> assertThat(maxQuotaManager().getDomainMaxStorage(DOMAIN))
            .isEqualTo(Optional.of(QuotaSizeLimit.unlimited())));
    }

    @Test
    default void shouldSupportSetUnlimitedRateLimitingForDomain() {
        String validMessage = String.format("""
        {
            "domain": "%s",
            "mailDnsConfigurationValidated": true,
            "features": {
                "mail": {
                    "storageQuota": 1000,
                    "mailsSentPerMinute": -1,
                    "mailsSentPerHour": -1,
                    "mailsSentPerDay": -1,
                    "mailsReceivedPerMinute": -1,
                    "mailsReceivedPerHour": -1,
                    "mailsReceivedPerDay": -1
                }
            }
        }
        """, DOMAIN.asString());

        publishAmqpSaaSDomainSubscriptionMessage(validMessage);

        await.untilAsserted(() -> {
            RateLimitingDefinition rateLimitingDefinition = Mono.from(rateLimitingRepository().getRateLimiting(DOMAIN)).block();
            assertThat(rateLimitingDefinition).isEqualTo(UNLIMITED);
        });
    }

    @Test
    default void shouldUpdateStorageQuotaWhenNewSubscriptionUpdate() throws MailboxException, DomainListException {
        domainList().addDomain(DOMAIN);
        maxQuotaManager().setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(1234));

        String validMessage = String.format("""
        {
            "domain": "%s",
            "mailDnsConfigurationValidated": true,
            "features": {
                "mail": {
                    "storageQuota": 12334534,
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

        await.untilAsserted(() -> assertThat(maxQuotaManager().getDomainMaxStorage(DOMAIN))
            .isEqualTo(Optional.of(QuotaSizeLimit.size(12334534))));
    }

    @Test
    default void shouldUpdateRateLimitingWhenNewSubscriptionUpdate() {
        String validMessage = String.format("""
        {
            "domain": "%s",
            "mailDnsConfigurationValidated": true,
            "features": {
                "mail": {
                    "storageQuota": 12334534,
                    "mailsSentPerMinute": 1,
                    "mailsSentPerHour": 10,
                    "mailsSentPerDay": 100,
                    "mailsReceivedPerMinute": 2,
                    "mailsReceivedPerHour": 20,
                    "mailsReceivedPerDay": 200
                }
            }
        }
        """, DOMAIN.asString());

        publishAmqpSaaSDomainSubscriptionMessage(validMessage);

        String validUpdateMessage = String.format("""
        {
            "domain": "%s",
            "mailDnsConfigurationValidated": true,
            "features": {
                "mail": {
                    "storageQuota": 12334534,
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

        publishAmqpSaaSDomainSubscriptionMessage(validUpdateMessage);

        await.untilAsserted(() -> {
            RateLimitingDefinition rateLimitingDefinition = Mono.from(rateLimitingRepository().getRateLimiting(DOMAIN)).block();
            assertThat(rateLimitingDefinition).isEqualTo(RATE_LIMITING_1);
        });
    }

    @Test
    default void shouldNotEffectOtherDomainSubscription() throws MailboxException, DomainListException {
        Domain otherDomain = Domain.of("otherDomain.org");
        domainList().addDomain(otherDomain);
        maxQuotaManager().setDomainMaxStorage(otherDomain, QuotaSizeLimit.size(1234));

        RateLimitingDefinition otherRateLimiting = RateLimitingDefinition.builder()
            .mailsSentPerMinute(15L)
            .mailsSentPerHours(150L)
            .mailsSentPerDays(1500L)
            .mailsReceivedPerMinute(25L)
            .mailsReceivedPerHours(250L)
            .mailsReceivedPerDays(2500L)
            .build();
        Mono.from(rateLimitingRepository().setRateLimiting(otherDomain, otherRateLimiting)).block();

        String validMessage = String.format("""
        {
            "domain": "%s",
            "mailDnsConfigurationValidated": true,
            "features": {
                "mail": {
                    "storageQuota": 12334534,
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
            assertThat(domainList().containsDomain(otherDomain)).isTrue();
            assertThat(maxQuotaManager().getDomainMaxStorage(otherDomain))
                .isEqualTo(Optional.of(QuotaSizeLimit.size(1234)));
            RateLimitingDefinition rateLimitingDefinition = Mono.from(rateLimitingRepository().getRateLimiting(otherDomain)).block();
            assertThat(rateLimitingDefinition).isEqualTo(otherRateLimiting);
        });
    }

    @Test
    default void validSubscriptionShouldBeIdempotent() {
        String validMessage = String.format("""
        {
            "domain": "%s",
            "mailDnsConfigurationValidated": true,
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

        await.untilAsserted(() -> {
            assertThat(domainList().containsDomain(DOMAIN)).isTrue();
            assertThat(maxQuotaManager().getDomainMaxStorage(DOMAIN))
                .isEqualTo(Optional.of(QuotaSizeLimit.size(1234)));
            RateLimitingDefinition rateLimitingDefinition = Mono.from(rateLimitingRepository().getRateLimiting(DOMAIN)).block();
            assertThat(rateLimitingDefinition).isEqualTo(RATE_LIMITING_1);
        });
    }

    @Test
    default void invalidSubscriptionAmqpMessageShouldNotCrashConsumer() {
        String invalidMessage = "{ invalid json }";
        publishAmqpSaaSDomainSubscriptionMessage(invalidMessage);

        // Publish a valid message after invalid one to ensure consumer is still alive
        String validMessage = String.format("""
        {
            "domain": "%s",
            "mailDnsConfigurationValidated": true,
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

        await.untilAsserted(() -> assertThat(domainList().containsDomain(DOMAIN)).isTrue());
    }

    @Test
    default void shouldRemoveDisabledDomain() throws DomainListException {
        domainList().addDomain(DOMAIN);

        String validMessage = String.format("""
        {
            "domain": "%s",
            "enabled": false
        }
        """, DOMAIN.asString());

        publishAmqpSaaSDomainSubscriptionMessage(validMessage);

        await.untilAsserted(() -> assertThat(domainList().containsDomain(DOMAIN)).isFalse());
    }

    @Test
    default void shouldNotRemoveEnabledDomain() throws DomainListException {
        domainList().addDomain(DOMAIN);

        String validMessage = String.format("""
        {
            "domain": "%s",
            "enabled": true
        }
        """, DOMAIN.asString());

        publishAmqpSaaSDomainSubscriptionMessage(validMessage);

        await.untilAsserted(() -> assertThat(domainList().containsDomain(DOMAIN)).isTrue());
    }

    @Test
    default void shouldNotAffectOtherDomainWhenRemovingDomain() throws DomainListException {
        Domain otherDomain = Domain.of("otherDomain.org");
        domainList().addDomain(DOMAIN);
        domainList().addDomain(otherDomain);

        String validMessage = String.format("""
        {
            "domain": "%s",
            "enabled": false
        }
        """, DOMAIN.asString());

        publishAmqpSaaSDomainSubscriptionMessage(validMessage);

        await.untilAsserted(() -> {
            assertThat(domainList().containsDomain(DOMAIN)).isFalse();
            assertThat(domainList().containsDomain(otherDomain)).isTrue();
        });
    }

    @Test
    default void cancelSubscriptionShouldBeIdempotent() throws DomainListException {
        domainList().addDomain(DOMAIN);

        String validMessage = String.format("""
        {
            "domain": "%s",
            "enabled": false
        }
        """, DOMAIN.asString());

        publishAmqpSaaSDomainSubscriptionMessage(validMessage);
        publishAmqpSaaSDomainSubscriptionMessage(validMessage);

        await.untilAsserted(() -> assertThat(domainList().containsDomain(DOMAIN)).isFalse());
    }

    @Test
    default void invalidAmqpMessageShouldNotCrashConsumer() throws DomainListException {
        domainList().addDomain(DOMAIN);

        String invalidMessage = "{ invalid json }";
        publishAmqpSaaSDomainSubscriptionMessage(invalidMessage);

        // Publish a valid message after invalid one to ensure consumer is still alive
        String validMessage = String.format("""
        {
            "domain": "%s",
            "enabled": false
        }
        """, DOMAIN.asString());

        publishAmqpSaaSDomainSubscriptionMessage(validMessage);

        await.untilAsserted(() -> assertThat(domainList().containsDomain(DOMAIN)).isFalse());
    }

    @Test
    default void verifyChainSaasDomainSubscriptionMessage() {
        String validMessage = String.format("""
            {
                "domain": "%s",
                "mailDnsConfigurationValidated": true,
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

        await.untilAsserted(() -> assertThat(domainList().containsDomain(DOMAIN)).isTrue());

        String cancelMessage = String.format("""
            {
                "domain": "%s",
                "enabled": false
            }
            """, DOMAIN.asString());

        publishAmqpSaaSDomainSubscriptionMessage(cancelMessage);

        await.untilAsserted(() -> assertThat(domainList().containsDomain(DOMAIN)).isFalse());
    }

    private void publishAmqpSaaSDomainSubscriptionMessage(String message) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(
                SaaSSubscriptionRabbitMQConfiguration.DEFAULT.exchange(),
                SaaSSubscriptionRabbitMQConfiguration.DEFAULT.domainRoutingKey(),
                message.getBytes(UTF_8))))
            .block();
    }

    private void publishAmqpSaaSConfigurationMessage(String message) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(
                SaaSSubscriptionRabbitMQConfiguration.DEFAULT.configurationExchange(),
                SaaSSubscriptionRabbitMQConfiguration.DEFAULT.domainConfigurationRoutingKey(),
                message.getBytes(UTF_8))))
            .block();
    }
}
