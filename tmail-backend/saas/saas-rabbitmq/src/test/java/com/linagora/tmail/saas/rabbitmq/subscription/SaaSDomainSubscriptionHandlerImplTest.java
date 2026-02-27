package com.linagora.tmail.saas.rabbitmq.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

import reactor.core.publisher.Mono;

public class SaaSDomainSubscriptionHandlerImplTest {

    public static final boolean DOMAIN_DISABLED = false;
    public static final boolean MAIL_DNS_CONFIGURATION_VALIDATED = true;

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

    @Test
    void shouldAddDomainAndApplySettingsWhenDnsValidatedAndFeaturesPresent() throws Exception {
        String domainName = "example.com";
        Domain domain = Domain.of(domainName);

        SaasFeatures.MailLimitation mail = new SaasFeatures.MailLimitation(
            123L,
            10L,
            100L,
            1000L,
            20L,
            200L,
            2000L);
        SaasFeatures features = new SaasFeatures(Optional.of(mail));

        SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message =
            new SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage(domainName, Optional.empty(), Optional.of(MAIL_DNS_CONFIGURATION_VALIDATED), Optional.of(features));

        handler.handleMessage(message).block();

        // Domain added
        assertThat(domainList.containsDomain(domain)).isTrue();

        // Storage quota applied
        assertThat(maxQuotaManager.getDomainMaxStorage(domain)).isEqualTo(Optional.of(QuotaSizeLimit.size(123L)));

        // Rate limiting applied
        RateLimitingDefinition rateLimitingDefinition = RateLimitingDefinition.builder()
            .mailsSentPerMinute(10L)
            .mailsSentPerHours(100L)
            .mailsSentPerDays(1000L)
            .mailsReceivedPerMinute(20L)
            .mailsReceivedPerHours(200L)
            .mailsReceivedPerDays(2000L)
            .build();

        // fetch from repository via blocking (same approach as consumer tests)
        RateLimitingDefinition actual = Mono.from(rateLimitingRepository.getRateLimiting(domain)).block();
        assertThat(actual).isEqualTo(rateLimitingDefinition);
    }

    @Test
    void shouldRemoveDomainWhenDomainIsDisabled() throws Exception {
        String domainName = "remove.com";
        Domain domain = Domain.of(domainName);

        // register domain
        domainList.addDomain(domain);
        assertThat(domainList.containsDomain(domain)).isTrue();

        SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage message =
            new SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage(domainName, Optional.empty(),DOMAIN_DISABLED);

        handler.handleMessage(message).block();

        assertThat(domainList.containsDomain(domain)).isFalse();
    }

    @Test
    void addingDomainShouldBeIdempotent() throws Exception {
        String domainName = "dup.com";
        Domain domain = Domain.of(domainName);

        SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message =
            new SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage(domainName, Optional.empty(), Optional.of(MAIL_DNS_CONFIGURATION_VALIDATED), Optional.empty());

        // First add
        handler.handleMessage(message).block();
        // Duplicate add should not throw and should remain present
        handler.handleMessage(message).block();

        assertThat(domainList.containsDomain(domain)).isTrue();
    }

    @Test
    void shouldNotThrowWhenRemovingNonexistentDomain() throws Exception {
        String domainName = "nonexistent.com";
        Domain domain = Domain.of(domainName);

        SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage message =
            new SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage(domainName, Optional.empty(), DOMAIN_DISABLED);

        // Should not throw
        handler.handleMessage(message).block();

        assertThat(domainList.containsDomain(domain)).isFalse();
    }
}
