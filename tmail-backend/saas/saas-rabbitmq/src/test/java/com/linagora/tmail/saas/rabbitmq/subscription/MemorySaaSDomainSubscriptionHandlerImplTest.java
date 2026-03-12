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

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.junit.jupiter.api.BeforeEach;

import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingRepository;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;

public class MemorySaaSDomainSubscriptionHandlerImplTest implements SaaSDomainSubscriptionHandlerImplContract {
    private DomainList domainList;
    private MaxQuotaManager maxQuotaManager;
    private RateLimitingRepository rateLimitingRepository;
    private SaaSAccountRepository saasAccountRepository;

    private SaaSDomainSubscriptionHandlerImpl handler;

    @BeforeEach
    void setUp() {
        domainList = new SimpleDomainList();
        maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        rateLimitingRepository = new MemoryRateLimitingRepository();
        saasAccountRepository = new MemorySaaSAccountRepository();

        handler = new SaaSDomainSubscriptionHandlerImpl(domainList, maxQuotaManager, rateLimitingRepository, saasAccountRepository);
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
    public SaaSAccountRepository saasAccountRepository() {
        return saasAccountRepository;
    }

    @Override
    public SaaSDomainSubscriptionHandlerImpl handler() {
        return handler;
    }
}
