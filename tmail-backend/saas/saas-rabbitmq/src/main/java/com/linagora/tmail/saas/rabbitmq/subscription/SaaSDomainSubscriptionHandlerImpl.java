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

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

import reactor.core.publisher.Mono;

public class SaaSDomainSubscriptionHandlerImpl implements SaaSDomainSubscriptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaaSDomainSubscriptionHandlerImpl.class);

    private final DomainList domainList;
    private final MaxQuotaManager maxQuotaManager;
    private final RateLimitingRepository rateLimitingRepository;

    public SaaSDomainSubscriptionHandlerImpl(DomainList domainList,
                                             MaxQuotaManager maxQuotaManager,
                                             RateLimitingRepository rateLimitingRepository) {
        this.domainList = domainList;
        this.maxQuotaManager = maxQuotaManager;
        this.rateLimitingRepository = rateLimitingRepository;
    }

    @Override
    public Mono<Void> handleMessage(SaaSDomainSubscriptionMessage domainSubscriptionMessage) {
        return switch (domainSubscriptionMessage) {
            case SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message  -> handleDomainValidSubscriptionMessage(message);
            case SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage message -> handleDomainCancelSubscriptionMessage(message);
            default -> throw new IllegalArgumentException("Unrecognized SaaS domain subscription message");
        };
    }

    private Mono<Void> handleDomainCancelSubscriptionMessage(SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage domainCancelSubscriptionMessage) {
        if (!domainCancelSubscriptionMessage.enabled()) {
            Domain domain = Domain.of(domainCancelSubscriptionMessage.domain());
            return removeDomainIfExists(domain)
                .doOnSuccess(success -> LOGGER.info("Cancelled SaaS subscription for domain: {}", domain));
        }
        return Mono.empty();
    }

    private Mono<Void> removeDomainIfExists(Domain domain) {
        return Mono.fromRunnable(Throwing.runnable(() -> domainList.removeDomain(domain)))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .then();
    }

    private Mono<Void> handleDomainValidSubscriptionMessage(SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message) {
        return createDomainIfValidated(message)
            .then(applyDomainSettings(message));
    }

    private Mono<Void> createDomainIfValidated(SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message) {
        Domain domain = Domain.of(message.domain());
        if (message.mailDnsConfigurationValidated().orElse(false)) {
            return addDomainIfNotExist(domain);
        }
        return Mono.empty();
    }

    private Mono<Void> applyDomainSettings(SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message) {
        Domain domain = Domain.of(message.domain());

        return message.features()
            .flatMap(SaasFeatures::mail)
            .map(mailSettings -> updateStorageDomainQuota(domain, mailSettings.storageQuota())
                .then(updateRateLimiting(domain, mailSettings.rateLimitingDefinition()))
                .doOnSuccess(success -> LOGGER.info("Updated SaaS subscription for domain: {}, storageQuota: {}, rateLimiting: {}",
                    domain, mailSettings.storageQuota(), mailSettings.rateLimitingDefinition()))).orElse(Mono.empty());
    }

    private Mono<Void> addDomainIfNotExist(Domain domain) {
        return Mono.from(domainList.containsDomainReactive(domain))
            .flatMap(alreadyExists -> {
                if (alreadyExists) {
                    return Mono.empty();
                }
                return Mono.fromRunnable(Throwing.runnable(() -> domainList.addDomain(domain)))
                    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
                    .then();
            });
    }

    private Mono<Void> updateStorageDomainQuota(Domain domain, Long storageQuota) {
        return Mono.from(maxQuotaManager.setDomainMaxStorageReactive(domain, SaaSSubscriptionUtils.asQuotaSizeLimit(storageQuota)));
    }

    private Mono<Void> updateRateLimiting(Domain domain, RateLimitingDefinition rateLimiting) {
        return Mono.from(rateLimitingRepository.setRateLimiting(domain, rateLimiting));
    }
}
