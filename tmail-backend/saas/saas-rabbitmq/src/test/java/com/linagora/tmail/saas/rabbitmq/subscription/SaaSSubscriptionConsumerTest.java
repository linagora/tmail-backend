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
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;
import static org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources.defaultResources;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.lib.UsersRepositoryContract;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;
import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;

class SaaSSubscriptionConsumerTest {
    private static final Username ALICE = Username.of("alice@james.org");
    private static final Username BOB = Username.of("bob@james.org");
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

    @RegisterExtension
    UsersRepositoryContract.UserRepositoryExtension userRepositoryExtension = UsersRepositoryContract.UserRepositoryExtension.withVirtualHost();

    private SaaSSubscriptionConsumer testee;
    private SaaSAccountRepository saasAccountRepository;
    private MaxQuotaManager maxQuotaManager;
    private UserQuotaRootResolver userQuotaRootResolver;
    private RateLimitingRepository rateLimitingRepository;

    @BeforeEach
    void setUp(UsersRepositoryContract.TestSystem testSystem) throws URISyntaxException, UsersRepositoryException {
        RabbitMQConfiguration rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(rabbitMQExtension.getRabbitMQ().amqpUri())
            .managementUri(rabbitMQExtension.getRabbitMQ().managementUri())
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();

        TWPCommonRabbitMQConfiguration twpCommonRabbitMQConfiguration = new TWPCommonRabbitMQConfiguration(
            Optional.empty(),
            Optional.empty(),
            false);
        saasAccountRepository = new MemorySaaSAccountRepository();
        UsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(testSystem.getDomainList());
        maxQuotaManager = new InMemoryPerUserMaxQuotaManager();

        InMemoryIntegrationResources resources = defaultResources();
        userQuotaRootResolver = resources.getDefaultUserQuotaRootResolver();

        usersRepository.addUser(ALICE, "password");
        usersRepository.addUser(BOB, "password");

        rateLimitingRepository = new MemoryRateLimitingRepository();

        testee = new SaaSSubscriptionConsumer(
            rabbitMQExtension.getRabbitChannelPool(),
            rabbitMQConfiguration,
            twpCommonRabbitMQConfiguration,
            SaaSSubscriptionRabbitMQConfiguration.DEFAULT,
            new SaaSSubscriptionHandlerImpl(usersRepository,
                saasAccountRepository,
                maxQuotaManager,
                userQuotaRootResolver,
                rateLimitingRepository));
        testee.init();
    }

    @AfterEach
    void tearDown() {
        testee.close();
    }

    @Test
    void shouldRegisterSaasAccountDetails() {
        String validMessage = String.format("""
            {
                "internalEmail": "%s",
                "isPaying": true,
                "canUpgrade": true,
                "features": {
                    "mail": {
                        "storageQuota": 123,
                        "mailsSentPerMinute": 10,
                        "mailsSentPerHour": 100,
                        "mailsSentPerDay": 1000,
                        "mailsReceivedPerMinute": 20,
                        "mailsReceivedPerHour": 200,
                        "mailsReceivedPerDay": 2000
                    }
                }
            }
            """, ALICE.asString());

        publishAmqpSaaSSubscriptionMessage(validMessage);

        await.untilAsserted(() -> {
            SaaSAccount saaSAccount = Mono.from(saasAccountRepository.getSaaSAccount(ALICE)).block();
            assertThat(saaSAccount).isNotNull();
            assertThat(saaSAccount.isPaying()).isTrue();
            assertThat(saaSAccount.canUpgrade()).isTrue();
        });
    }

    @Test
    void shouldSetStorageQuotaWhenUserHasNoPlanYet() {
        String validMessage = String.format("""
            {
                "internalEmail": "%s",
                "isPaying": true,
                "canUpgrade": true,
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
            """, ALICE.asString());

        publishAmqpSaaSSubscriptionMessage(validMessage);

        await.untilAsserted(() -> assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(ALICE)))
            .isEqualTo(Optional.of(QuotaSizeLimit.size(1234))));
    }

    @Test
    void shouldSetRateLimitingWhenUserHasNoPlanYet() {
        String validMessage = String.format("""
            {
                "internalEmail": "%s",
                "isPaying": true,
                "canUpgrade": true,
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
            """, ALICE.asString());

        publishAmqpSaaSSubscriptionMessage(validMessage);

        await.untilAsserted(() -> {
            RateLimitingDefinition rateLimitingDefinition = Mono.from(rateLimitingRepository.getRateLimiting(ALICE)).block();
            assertThat(rateLimitingDefinition).isEqualTo(RATE_LIMITING_1);
        });
    }

    @Test
    void shouldSupportSetUnlimitedStorageQuota() {
        String validMessage = String.format("""
            {
                "internalEmail": "%s",
                "isPaying": true,
                "canUpgrade": true,
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
            """, ALICE.asString());

        publishAmqpSaaSSubscriptionMessage(validMessage);

        await.untilAsserted(() -> assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(ALICE)))
            .isEqualTo(Optional.of(QuotaSizeLimit.unlimited())));
    }

    @Test
    void shouldSupportSetUnlimitedRateLimiting() {
        String validMessage = String.format("""
            {
                "internalEmail": "%s",
                "isPaying": true,
                "canUpgrade": true,
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
            """, ALICE.asString());

        publishAmqpSaaSSubscriptionMessage(validMessage);

        await.untilAsserted(() -> {
            RateLimitingDefinition rateLimitingDefinition = Mono.from(rateLimitingRepository.getRateLimiting(ALICE)).block();
            assertThat(rateLimitingDefinition).isEqualTo(UNLIMITED);
        });
    }

    @Test
    void shouldUpdateCanUpgradeWhenNewSubscriptionUpdate() {
        Mono.from(saasAccountRepository.upsertSaasAccount(ALICE,
            new SaaSAccount(true, true))).block();

        String validMessage = String.format("""
            {
                "internalEmail": "%s",
                "isPaying": true,
                "canUpgrade": false,
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
            """, ALICE.asString());

        publishAmqpSaaSSubscriptionMessage(validMessage);

        await.untilAsserted(() -> {
            SaaSAccount saaSAccount = Mono.from(saasAccountRepository.getSaaSAccount(ALICE)).block();
            assertThat(saaSAccount).isNotNull();
            assertThat(saaSAccount.canUpgrade()).isFalse();
        });
    }

    @Test
    void shouldUpdateStorageQuotaWhenNewSubscriptionUpdate() throws MailboxException {
        maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(ALICE), QuotaSizeLimit.size(1234));

        String validMessage = String.format("""
            {
                "internalEmail": "%s",
                "isPaying": true,
                "canUpgrade": false,
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
            """, ALICE.asString());

        publishAmqpSaaSSubscriptionMessage(validMessage);

        await.untilAsserted(() -> assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(ALICE)))
            .isEqualTo(Optional.of(QuotaSizeLimit.size(12334534))));
    }

    @Test
    void shouldUpdateRateLimitingWhenNewSubscriptionUpdate() {
        publishAmqpSaaSSubscriptionMessage(String.format("""
            {
                "internalEmail": "%s",
                "isPaying": true,
                "canUpgrade": false,
                "features": {
                    "mail": {
                        "storageQuota": 12334534,
                        "mailsSentPerMinute": 1,
                        "mailsSentPerHour": 1,
                        "mailsSentPerDay": 1,
                        "mailsReceivedPerMinute": 2,
                        "mailsReceivedPerHour": 2,
                        "mailsReceivedPerDay": 2
                    }
                }
            }
            """, ALICE.asString()));

        publishAmqpSaaSSubscriptionMessage(String.format("""
            {
                "internalEmail": "%s",
                "isPaying": true,
                "canUpgrade": false,
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
            """, ALICE.asString()));

        await.untilAsserted(() -> {
            RateLimitingDefinition rateLimitingDefinition = Mono.from(rateLimitingRepository.getRateLimiting(ALICE)).block();
            assertThat(rateLimitingDefinition).isEqualTo(RATE_LIMITING_1);
        });
    }

    @Test
    void shouldNotEffectOtherUserSubscription() throws MailboxException {
        Mono.from(saasAccountRepository.upsertSaasAccount(ALICE,
            new SaaSAccount(true, true))).block();
        maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(ALICE), QuotaSizeLimit.size(1234));

        // Update Bob subscription should not effect Alice subscription
        String validMessage = String.format("""
            {
                "internalEmail": "%s",
                "isPaying": true,
                "canUpgrade": false,
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
            """, BOB.asString());
        publishAmqpSaaSSubscriptionMessage(validMessage);

        await.untilAsserted(() -> {
            SaaSAccount saaSAccount = Mono.from(saasAccountRepository.getSaaSAccount(ALICE)).block();
            assertThat(saaSAccount).isNotNull();
            assertThat(saaSAccount.isPaying()).isTrue();
            assertThat(saaSAccount.canUpgrade()).isTrue();
            assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(ALICE)))
                .isEqualTo(Optional.of(QuotaSizeLimit.size(1234)));
        });
    }

    @Test
    void shouldBeIdempotent() {
        String validMessage = String.format("""
            {
                "internalEmail": "%s",
                "isPaying": true,
                "canUpgrade": false,
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
            """, ALICE.asString());

        publishAmqpSaaSSubscriptionMessage(validMessage);
        publishAmqpSaaSSubscriptionMessage(validMessage);

        await.untilAsserted(() -> {
            SaaSAccount saaSAccount = Mono.from(saasAccountRepository.getSaaSAccount(ALICE)).block();
            assertThat(saaSAccount).isNotNull();
            assertThat(saaSAccount.canUpgrade()).isFalse();
            assertThat(maxQuotaManager.getMaxStorage(userQuotaRootResolver.forUser(ALICE)))
                .isEqualTo(Optional.of(QuotaSizeLimit.size(1234)));
            RateLimitingDefinition rateLimitingDefinition = Mono.from(rateLimitingRepository.getRateLimiting(ALICE)).block();
            assertThat(rateLimitingDefinition).isEqualTo(RATE_LIMITING_1);
        });
    }

    @Test
    void invalidAmqpMessageShouldNotCrashConsumer() {
        String invalidMessage = "{ invalid json }";
        publishAmqpSaaSSubscriptionMessage(invalidMessage);

        // Publish a valid message after invalid one to ensure consumer is still alive
        String validMessage = String.format("""
            {
                "internalEmail": "%s",
                "isPaying": true,
                "canUpgrade": false,
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
            """, ALICE.asString());
        publishAmqpSaaSSubscriptionMessage(validMessage);
        await.untilAsserted(() -> {
            SaaSAccount saaSAccount = Mono.from(saasAccountRepository.getSaaSAccount(ALICE)).block();
            assertThat(saaSAccount).isNotNull();
            assertThat(saaSAccount.canUpgrade()).isFalse();
        });
    }

    private void publishAmqpSaaSSubscriptionMessage(String message) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(
                SaaSSubscriptionRabbitMQConfiguration.TWP_SAAS_SUBSCRIPTION_EXCHANGE_DEFAULT,
                SaaSSubscriptionRabbitMQConfiguration.TWP_SAAS_SUBSCRIPTION_ROUTING_KEY_DEFAULT,
                message.getBytes(UTF_8))))
            .block();
    }
}
