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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

import reactor.core.publisher.Mono;

public interface SaaSDomainSubscriptionHandlerImplContract {
    boolean DOMAIN_DISABLED = false;
    boolean MAIL_DNS_CONFIGURATION_VALIDATED = true;

    DomainList domainList();
    MaxQuotaManager maxQuotaManager();
    RateLimitingRepository rateLimitingRepository();

    SaaSDomainSubscriptionHandlerImpl handler();

    @Test
    default void shouldAddDomainAndApplySettingsWhenDnsValidatedAndFeaturesPresent() throws Exception {
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

        handler().handleMessage(message).block();

        // Domain added
        assertThat(domainList().containsDomain(domain)).isTrue();

        // Storage quota applied
        assertThat(maxQuotaManager().getDomainMaxStorage(domain)).isEqualTo(Optional.of(QuotaSizeLimit.size(123L)));

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
        RateLimitingDefinition actual = Mono.from(rateLimitingRepository().getRateLimiting(domain)).block();
        assertThat(actual).isEqualTo(rateLimitingDefinition);
    }

    @Test
    default void shouldRemoveDomainWhenDomainIsDisabled() throws Exception {
        String domainName = "remove.com";
        Domain domain = Domain.of(domainName);

        // register domain
        domainList().addDomain(domain);
        assertThat(domainList().containsDomain(domain)).isTrue();

        SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage message =
            new SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage(domainName, Optional.empty(),DOMAIN_DISABLED);

        handler().handleMessage(message).block();

        assertThat(domainList().containsDomain(domain)).isFalse();
    }

    @Test
    default void addingDomainShouldBeIdempotent() throws Exception {
        String domainName = "dup.com";
        Domain domain = Domain.of(domainName);

        SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message =
            new SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage(domainName, Optional.empty(), Optional.of(MAIL_DNS_CONFIGURATION_VALIDATED), Optional.empty());

        // First add
        handler().handleMessage(message).block();
        // Duplicate add should not throw and should remain present
        handler().handleMessage(message).block();

        assertThat(domainList().containsDomain(domain)).isTrue();
    }

    @Test
    default void shouldNotThrowWhenRemovingNonexistentDomain() throws Exception {
        String domainName = "nonexistent.com";
        Domain domain = Domain.of(domainName);

        SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage message =
            new SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage(domainName, Optional.empty(), DOMAIN_DISABLED);

        // Should not throw
        handler().handleMessage(message).block();

        assertThat(domainList().containsDomain(domain)).isFalse();
    }
}
