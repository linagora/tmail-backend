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

import java.net.URISyntaxException;
import java.util.Optional;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.junit.jupiter.api.BeforeEach;

import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingRepository;
import com.linagora.tmail.saas.rabbitmq.TWPCommonRabbitMQConfiguration;

class MemorySaaSDomainSubscriptionConsumerTest implements SaaSDomainSubscriptionConsumerContract {
    private DomainList domainList;
    private MaxQuotaManager maxQuotaManager;
    private RateLimitingRepository rateLimitingRepository;
    private SaaSDomainSubscriptionConsumer testee;

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

        maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        domainList = new SimpleDomainList();

        rateLimitingRepository = new MemoryRateLimitingRepository();

        testee = new SaaSDomainSubscriptionConsumer(
            rabbitMQExtension.getRabbitChannelPool(),
            rabbitMQConfiguration,
            twpCommonRabbitMQConfiguration,
            SaaSSubscriptionRabbitMQConfiguration.DEFAULT,
            new SaaSDomainSubscriptionHandlerImpl(domainList,
                maxQuotaManager,
                rateLimitingRepository));
        testee.init();
    }


    @Override
    public DomainList domainList() {
        return domainList;
    }

    @Override
    public MaxQuotaManager maxQuotaManager() {
        return maxQuotaManager;
    }

    @Override
    public RateLimitingRepository rateLimitingRepository() {
        return rateLimitingRepository;
    }

    @Override
    public SaaSDomainSubscriptionConsumer testee() {
        return testee;
    }
}
