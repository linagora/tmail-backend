package com.linagora.tmail.saas.rabbitmq.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.SaaSDomainAccountRepository;
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.api.memory.MemorySaaSDomainAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class SaaSDomainSubscriptionHandlerImplTest {

    public static final boolean DOMAIN_DISABLED = false;
    public static final boolean MAIL_DNS_CONFIGURATION_VALIDATED = true;

    private DomainList domainList;
    private MaxQuotaManager maxQuotaManager;
    private RateLimitingRepository rateLimitingRepository;
    private UsersRepository usersRepository;
    private SaaSAccountRepository saaSAccountRepository;
    private SaaSDomainAccountRepository saaSDomainAccountRepository;

    private SaaSDomainSubscriptionHandlerImpl handler;

    @BeforeEach
    void setUp() throws Exception {
        domainList = new SimpleDomainList();
        maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        rateLimitingRepository = new MemoryRateLimitingRepository();
        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        saaSAccountRepository = new MemorySaaSAccountRepository();
        saaSDomainAccountRepository = new MemorySaaSDomainAccountRepository();

        handler = new SaaSDomainSubscriptionHandlerImpl(domainList, maxQuotaManager, rateLimitingRepository,
            usersRepository, saaSAccountRepository, saaSDomainAccountRepository);
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
            new SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage(domainName,
                Optional.of(MAIL_DNS_CONFIGURATION_VALIDATED), Optional.of(features),
                Optional.empty(), Optional.empty());

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
            new SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage(domainName, DOMAIN_DISABLED);

        handler.handleMessage(message).block();

        assertThat(domainList.containsDomain(domain)).isFalse();
    }

    @Test
    void addingDomainShouldBeIdempotent() throws Exception {
        String domainName = "dup.com";
        Domain domain = Domain.of(domainName);

        SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message =
            new SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage(domainName,
                Optional.of(MAIL_DNS_CONFIGURATION_VALIDATED), Optional.empty(),
                Optional.empty(), Optional.empty());

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
            new SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage(domainName, DOMAIN_DISABLED);

        // Should not throw
        handler.handleMessage(message).block();

        assertThat(domainList.containsDomain(domain)).isFalse();
    }

    @Test
    void shouldApplyAccountSettingsToExistingUsers() throws Exception {
        String domainName = "account.com";
        Domain domain = Domain.of(domainName);
        domainList.addDomain(domain);

        Username bob = Username.of("bob@account.com");
        Username alice = Username.of("alice@account.com");
        usersRepository.addUser(bob, "password");
        usersRepository.addUser(alice, "password");

        SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message =
            new SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage(domainName,
                Optional.empty(), Optional.empty(),
                Optional.of(false), Optional.of(true));

        handler.handleMessage(message).block();

        SaaSAccount expectedAccount = new SaaSAccount(false, true);
        assertThat(Mono.from(saaSAccountRepository.getSaaSAccount(bob)).block()).isEqualTo(expectedAccount);
        assertThat(Mono.from(saaSAccountRepository.getSaaSAccount(alice)).block()).isEqualTo(expectedAccount);
    }

    @Test
    void shouldStoreDomainDefaultsWhenAccountSettingsPresent() throws Exception {
        String domainName = "defaults.com";
        Domain domain = Domain.of(domainName);
        domainList.addDomain(domain);

        SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message =
            new SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage(domainName,
                Optional.empty(), Optional.empty(),
                Optional.of(false), Optional.of(true));

        handler.handleMessage(message).block();

        SaaSAccount domainAccount = Mono.from(saaSDomainAccountRepository.getSaaSDomainAccount(domain)).block();
        assertThat(domainAccount).isEqualTo(new SaaSAccount(false, true));
    }

    @Test
    void shouldNotApplyAccountSettingsWhenBothFieldsAbsent() throws Exception {
        String domainName = "nofields.com";
        Domain domain = Domain.of(domainName);
        domainList.addDomain(domain);

        Username bob = Username.of("bob@nofields.com");
        usersRepository.addUser(bob, "password");

        SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message =
            new SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage(domainName,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());

        handler.handleMessage(message).block();

        // SaaS account should remain at default since no account settings were applied
        assertThat(Mono.from(saaSAccountRepository.getSaaSAccount(bob)).block()).isEqualTo(SaaSAccount.DEFAULT);
    }

    @Test
    void shouldSucceedWhenDomainHasNoUsers() throws Exception {
        String domainName = "empty.com";
        domainList.addDomain(Domain.of(domainName));

        SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message =
            new SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage(domainName,
                Optional.empty(), Optional.empty(),
                Optional.of(true), Optional.of(true));

        // Should not throw
        handler.handleMessage(message).block();

        SaaSAccount domainAccount = Mono.from(saaSDomainAccountRepository.getSaaSDomainAccount(Domain.of(domainName))).block();
        assertThat(domainAccount).isEqualTo(new SaaSAccount(true, true));
    }

    @Test
    void shouldDeleteDomainDefaultsOnCancellation() throws Exception {
        String domainName = "cancel.com";
        Domain domain = Domain.of(domainName);
        domainList.addDomain(domain);

        // First set domain defaults
        Mono.from(saaSDomainAccountRepository.upsertSaasDomainAccount(domain, new SaaSAccount(false, true))).block();

        SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage message =
            new SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage(domainName, DOMAIN_DISABLED);

        handler.handleMessage(message).block();

        assertThat(Mono.from(saaSDomainAccountRepository.getSaaSDomainAccount(domain)).block()).isNull();
    }

    @Test
    void shouldUseDefaultCanUpgradeWhenOnlyIsPayingProvided() throws Exception {
        String domainName = "partial.com";
        Domain domain = Domain.of(domainName);
        domainList.addDomain(domain);

        Username bob = Username.of("bob@partial.com");
        usersRepository.addUser(bob, "password");

        SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message =
            new SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage(domainName,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(true));

        handler.handleMessage(message).block();

        // canUpgrade should use DEFAULT value (true), isPaying should be true
        SaaSAccount expectedAccount = new SaaSAccount(SaaSAccount.DEFAULT.canUpgrade(), true);
        assertThat(Mono.from(saaSAccountRepository.getSaaSAccount(bob)).block()).isEqualTo(expectedAccount);
    }
}
