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

import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;
import static org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources.defaultResources;

import java.net.URISyntaxException;
import java.util.Optional;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.junit.jupiter.api.BeforeEach;

import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingRepository;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;

class MemorySaaSSubscriptionConsumerTest implements SaaSSubscriptionConsumerContract {
    private SaaSSubscriptionConsumer testee;
    private SaaSAccountRepository saasAccountRepository;
    private MaxQuotaManager maxQuotaManager;
    private UserQuotaRootResolver userQuotaRootResolver;
    private RateLimitingRepository rateLimitingRepository;

    @BeforeEach
    void setUp() throws URISyntaxException {
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
        maxQuotaManager = new InMemoryPerUserMaxQuotaManager();

        InMemoryIntegrationResources resources = defaultResources();
        userQuotaRootResolver = resources.getDefaultUserQuotaRootResolver();

        rateLimitingRepository = new MemoryRateLimitingRepository();

        testee = new SaaSSubscriptionConsumer(
            rabbitMQExtension.getRabbitChannelPool(),
            rabbitMQConfiguration,
            twpCommonRabbitMQConfiguration,
            SaaSSubscriptionRabbitMQConfiguration.DEFAULT,
            new SaaSSubscriptionHandlerImpl(saasAccountRepository,
                maxQuotaManager,
                userQuotaRootResolver,
                rateLimitingRepository));
        testee.init();
    }

    @Override
    public SaaSSubscriptionConsumer testee() {
        return testee;
    }

    @Override
    public SaaSAccountRepository saasAccountRepository() {
        return saasAccountRepository;
    }

    @Override
    public MaxQuotaManager maxQuotaManager() {
        return maxQuotaManager;
    }

    @Override
    public UserQuotaRootResolver userQuotaRootResolver() {
        return userQuotaRootResolver;
    }

    @Override
    public RateLimitingRepository rateLimitingRepository() {
        return rateLimitingRepository;
    }
}
