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

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class SaaSDomainSubscriptionHandlerImpl implements SaaSMessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaaSDomainSubscriptionHandlerImpl.class);

    private final DomainList domainList;
    private final MaxQuotaManager maxQuotaManager;
    private final RateLimitingRepository rateLimitingRepository;

    @Inject
    public SaaSDomainSubscriptionHandlerImpl(DomainList domainList,
                                             MaxQuotaManager maxQuotaManager,
                                             RateLimitingRepository rateLimitingRepository) {
        this.domainList = domainList;
        this.maxQuotaManager = maxQuotaManager;
        this.rateLimitingRepository = rateLimitingRepository;
    }

    @Override
    public Mono<Void> handleMessage(byte[] message) {
        return Mono.fromCallable(() -> SaaSSubscriptionDeserializer.parseAMQPDomainMessage(message))
            .doOnNext(parsed -> LOGGER.debug("Received SaaS domain subscription message: {}", parsed))
            .flatMap(this::handleMessage);
    }

    public Mono<Void> handleMessage(SaaSDomainSubscriptionMessage domainSubscriptionMessage) {
        Context context = ReactorUtils.context("saas-domain", MDCBuilder.create()
            .addToContext("domain", domainSubscriptionMessage.domain())
            .addToContextIfPresent("organizationId", domainSubscriptionMessage.organizationId()));

        return switch (domainSubscriptionMessage) {
            case SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message  -> ReactorUtils.logAsMono(() -> LOGGER.info("Processing valid subscription message for domain: {}, mailDnsConfigurationValidated: {}, hasFeatures: {}",
                    message.domain(), message.mailDnsConfigurationValidated().orElse(null), message.features().isPresent()))
                .then(handleDomainValidSubscriptionMessage(message))
                .contextWrite(context);
            case SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage message ->
                ReactorUtils.logAsMono(() -> LOGGER.info("Processing cancel subscription message for domain: {}, enabled: {}", message.domain(), message.enabled()))
                    .then(handleDomainCancelSubscriptionMessage(message))
                    .contextWrite(context);
            default -> throw new IllegalArgumentException("Unrecognized SaaS domain subscription message");
        };
    }

    private Mono<Void> handleDomainCancelSubscriptionMessage(SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage domainCancelSubscriptionMessage) {
        if (!domainCancelSubscriptionMessage.enabled()) {
            Domain domain = Domain.of(domainCancelSubscriptionMessage.domain());
            return ReactorUtils.logAsMono(() -> LOGGER.info("Processing domain cancellation for domain: {}", domain))
                .then(removeDomainIfExists(domain))
                .then(ReactorUtils.logAsMono(() -> LOGGER.info("Cancelled SaaS subscription for domain: {}", domain)));
        }
        return ReactorUtils.logAsMono(() -> LOGGER.info("Skipping domain cancellation for domain: {} because enabled is true", domainCancelSubscriptionMessage.domain()));
    }

    private Mono<Void> removeDomainIfExists(Domain domain) {
        return Mono.from(domainList.containsDomainReactive(domain))
            .flatMap(alreadyExists -> {
                if (!alreadyExists) {
                    return ReactorUtils.logAsMono(() -> LOGGER.info("Domain {} does not exist, skipping removal", domain));
                }
                return ReactorUtils.logAsMono(() -> LOGGER.info("Removing domain: {}", domain))
                    .then(Mono.fromRunnable(Throwing.runnable(() -> domainList.removeDomain(domain))))
                    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
                    .then();
            });
    }

    private Mono<Void> handleDomainValidSubscriptionMessage(SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message) {
        return createDomainIfValidated(message)
            .then(applyDomainSettings(message));
    }

    private Mono<Void> createDomainIfValidated(SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message) {
        Domain domain = Domain.of(message.domain());
        if (message.mailDnsConfigurationValidated().orElse(false)) {
            return ReactorUtils.logAsMono(() -> LOGGER.info("mailDnsConfigurationValidated is true for domain: {}, attempting to create domain", domain))
                .then(addDomainIfNotExist(domain));
        }
        return ReactorUtils.logAsMono(() -> LOGGER.info("Skipping domain creation for domain: {} because mailDnsConfigurationValidated is false or missing", domain));
    }

    private Mono<Void> applyDomainSettings(SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message) {
        Domain domain = Domain.of(message.domain());

        return message.features()
            .flatMap(SaasFeatures::mail)
            .map(mailSettings -> ReactorUtils.logAsMono(() -> LOGGER.info("Applying domain settings for domain: {}, storageQuota: {}, rateLimiting: {}",
                    domain, mailSettings.storageQuota(), mailSettings.rateLimitingDefinition()))
                .then(updateStorageDomainQuota(domain, mailSettings.storageQuota()))
                .then(updateRateLimiting(domain, mailSettings.rateLimitingDefinition()))
                .then(ReactorUtils.logAsMono(() -> LOGGER.info("Successfully updated SaaS subscription for domain: {}", domain))))
            .orElseGet(() -> ReactorUtils.logAsMono(() ->  LOGGER.info("Skipping domain settings for domain: {} because features.mail is missing", domain)));
    }

    private Mono<Void> addDomainIfNotExist(Domain domain) {
        return Mono.from(domainList.containsDomainReactive(domain))
            .flatMap(alreadyExists -> {
                if (alreadyExists) {
                    return ReactorUtils.logAsMono(() -> LOGGER.info("Domain {} already exists, skipping creation", domain));
                }
                return Mono.fromRunnable(Throwing.runnable(() -> domainList.addDomain(domain)))
                    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
                    .then(ReactorUtils.logAsMono(() -> LOGGER.info("Successfully created domain: {}", domain)))
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
