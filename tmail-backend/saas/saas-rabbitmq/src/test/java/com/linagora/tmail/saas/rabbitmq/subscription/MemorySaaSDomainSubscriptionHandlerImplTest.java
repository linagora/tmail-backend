package com.linagora.tmail.saas.rabbitmq.subscription;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.junit.jupiter.api.BeforeEach;

import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingRepository;

public class MemorySaaSDomainSubscriptionHandlerImplTest implements SaaSDomainSubscriptionHandlerImplContract {
    private DomainList domainList;
    private MaxQuotaManager maxQuotaManager;
    private RateLimitingRepository rateLimitingRepository;

    private SaaSDomainSubscriptionHandlerImpl handler;

    @BeforeEach
    void setUp() {
        domainList = new SimpleDomainList();
        maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        rateLimitingRepository = new MemoryRateLimitingRepository();

        handler = new SaaSDomainSubscriptionHandlerImpl(domainList, maxQuotaManager, rateLimitingRepository);
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
    public SaaSDomainSubscriptionHandlerImpl handler() {
        return handler;
    }
}
