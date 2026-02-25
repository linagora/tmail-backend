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
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.SaaSDomainAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SaaSDomainSubscriptionHandlerImpl implements SaaSMessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaaSDomainSubscriptionHandlerImpl.class);
    private static final int DEFAULT_CONCURRENCY = 16;

    private final DomainList domainList;
    private final MaxQuotaManager maxQuotaManager;
    private final RateLimitingRepository rateLimitingRepository;
    private final UsersRepository usersRepository;
    private final SaaSAccountRepository saaSAccountRepository;
    private final SaaSDomainAccountRepository saaSDomainAccountRepository;

    @Inject
    public SaaSDomainSubscriptionHandlerImpl(DomainList domainList,
                                             MaxQuotaManager maxQuotaManager,
                                             RateLimitingRepository rateLimitingRepository,
                                             UsersRepository usersRepository,
                                             SaaSAccountRepository saaSAccountRepository,
                                             SaaSDomainAccountRepository saaSDomainAccountRepository) {
        this.domainList = domainList;
        this.maxQuotaManager = maxQuotaManager;
        this.rateLimitingRepository = rateLimitingRepository;
        this.usersRepository = usersRepository;
        this.saaSAccountRepository = saaSAccountRepository;
        this.saaSDomainAccountRepository = saaSDomainAccountRepository;
    }

    @Override
    public Mono<Void> handleMessage(byte[] message) {
        return Mono.fromCallable(() -> SaaSSubscriptionDeserializer.parseAMQPDomainMessage(message))
            .doOnNext(parsed -> LOGGER.debug("Received SaaS domain subscription message: {}", parsed))
            .flatMap(this::handleMessage);
    }

    public Mono<Void> handleMessage(SaaSDomainSubscriptionMessage domainSubscriptionMessage) {
        return switch (domainSubscriptionMessage) {
            case SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message  -> {
                LOGGER.info("Processing valid subscription message for domain: {}, mailDnsConfigurationValidated: {}, hasFeatures: {}",
                    message.domain(), message.mailDnsConfigurationValidated().orElse(null), message.features().isPresent());
                yield handleDomainValidSubscriptionMessage(message);
            }
            case SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage message -> {
                LOGGER.info("Processing cancel subscription message for domain: {}, enabled: {}",
                    message.domain(), message.enabled());
                yield handleDomainCancelSubscriptionMessage(message);
            }
            default -> throw new IllegalArgumentException("Unrecognized SaaS domain subscription message");
        };
    }

    private Mono<Void> handleDomainCancelSubscriptionMessage(SaaSDomainSubscriptionMessage.SaaSDomainCancelSubscriptionMessage domainCancelSubscriptionMessage) {
        if (!domainCancelSubscriptionMessage.enabled()) {
            Domain domain = Domain.of(domainCancelSubscriptionMessage.domain());
            LOGGER.info("Processing domain cancellation for domain: {}", domain);
            return removeDomainIfExists(domain)
                .then(Mono.from(saaSDomainAccountRepository.deleteSaaSDomainAccount(domain)))
                .doOnSuccess(success -> LOGGER.info("Cancelled SaaS subscription for domain: {}", domain));
        }
        LOGGER.info("Skipping domain cancellation for domain: {} because enabled is true", domainCancelSubscriptionMessage.domain());
        return Mono.empty();
    }

    private Mono<Void> removeDomainIfExists(Domain domain) {
        return Mono.from(domainList.containsDomainReactive(domain))
            .flatMap(alreadyExists -> {
                if (!alreadyExists) {
                    LOGGER.info("Domain {} does not exist, skipping removal", domain);
                    return Mono.empty();
                }
                LOGGER.info("Removing domain: {}", domain);
                return Mono.fromRunnable(Throwing.runnable(() -> domainList.removeDomain(domain)))
                    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
                    .then();
            });
    }

    private Mono<Void> handleDomainValidSubscriptionMessage(SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message) {
        return createDomainIfValidated(message)
            .then(applyDomainSettings(message))
            .then(applyAccountSettings(message));
    }

    private Mono<Void> createDomainIfValidated(SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message) {
        Domain domain = Domain.of(message.domain());
        if (message.mailDnsConfigurationValidated().orElse(false)) {
            LOGGER.info("mailDnsConfigurationValidated is true for domain: {}, attempting to create domain", domain);
            return addDomainIfNotExist(domain);
        }
        LOGGER.info("Skipping domain creation for domain: {} because mailDnsConfigurationValidated is false or missing", domain);
        return Mono.empty();
    }

    private Mono<Void> applyDomainSettings(SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message) {
        Domain domain = Domain.of(message.domain());

        return message.features()
            .flatMap(SaasFeatures::mail)
            .map(mailSettings -> {
                LOGGER.info("Applying domain settings for domain: {}, storageQuota: {}, rateLimiting: {}",
                    domain, mailSettings.storageQuota(), mailSettings.rateLimitingDefinition());
                return updateStorageDomainQuota(domain, mailSettings.storageQuota())
                    .then(updateRateLimiting(domain, mailSettings.rateLimitingDefinition()))
                    .doOnSuccess(success -> LOGGER.info("Successfully updated SaaS subscription for domain: {}", domain));
            }).orElseGet(() -> {
                LOGGER.info("Skipping domain settings for domain: {} because features.mail is missing", domain);
                return Mono.empty();
            });
    }

    private Mono<Void> applyAccountSettings(SaaSDomainSubscriptionMessage.SaaSDomainValidSubscriptionMessage message) {
        if (message.canUpgrade().isEmpty() && message.isPaying().isEmpty()) {
            LOGGER.info("Skipping account settings for domain: {} because canUpgrade and isPaying are absent", message.domain());
            return Mono.empty();
        }

        Domain domain = Domain.of(message.domain());
        SaaSAccount account = new SaaSAccount(
            message.canUpgrade().orElse(SaaSAccount.DEFAULT.canUpgrade()),
            message.isPaying().orElse(SaaSAccount.DEFAULT.isPaying()));

        LOGGER.info("Applying account settings for domain: {}, canUpgrade: {}, isPaying: {}", domain, account.canUpgrade(), account.isPaying());

        return Mono.from(saaSDomainAccountRepository.upsertSaasDomainAccount(domain, account))
            .then(Flux.from(usersRepository.listUsersOfADomainReactive(domain))
                .flatMap(user -> Mono.from(saaSAccountRepository.upsertSaasAccount(user, account)), DEFAULT_CONCURRENCY)
                .then())
            .doOnSuccess(success -> LOGGER.info("Successfully applied account settings for domain: {}", domain));
    }

    private Mono<Void> addDomainIfNotExist(Domain domain) {
        return Mono.from(domainList.containsDomainReactive(domain))
            .flatMap(alreadyExists -> {
                if (alreadyExists) {
                    LOGGER.info("Domain {} already exists, skipping creation", domain);
                    return Mono.empty();
                }
                return Mono.fromRunnable(Throwing.runnable(() -> domainList.addDomain(domain)))
                    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
                    .doOnSuccess(any -> LOGGER.info("Successfully created domain: {}", domain))
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
