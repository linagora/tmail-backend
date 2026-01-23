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

import org.apache.james.core.Username;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.model.User;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class SaaSSubscriptionHandlerImpl implements SaaSSubscriptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaaSSubscriptionHandlerImpl.class);

    private final UsersRepository usersRepository;
    private final SaaSAccountRepository saasAccountRepository;
    private final MaxQuotaManager maxQuotaManager;
    private final UserQuotaRootResolver userQuotaRootResolver;
    private final RateLimitingRepository rateLimitingRepository;

    @Inject
    public SaaSSubscriptionHandlerImpl(UsersRepository usersRepository,
                                       SaaSAccountRepository saasAccountRepository,
                                       MaxQuotaManager maxQuotaManager,
                                       UserQuotaRootResolver userQuotaRootResolver,
                                       RateLimitingRepository rateLimitingRepository) {
        this.usersRepository = usersRepository;
        this.saasAccountRepository = saasAccountRepository;
        this.maxQuotaManager = maxQuotaManager;
        this.userQuotaRootResolver = userQuotaRootResolver;
        this.rateLimitingRepository = rateLimitingRepository;
    }

    @Override
    public Mono<Void> handleMessage(SaaSSubscriptionMessage subscriptionMessage) {
        SaaSAccount saaSAccount = new SaaSAccount(subscriptionMessage.canUpgrade(), subscriptionMessage.isPaying());
        return Mono.fromCallable(() -> usersRepository.getUserByName(Username.of(subscriptionMessage.internalEmail())))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .map(User::getUserName)
            .flatMap(username -> Mono.from(saasAccountRepository.upsertSaasAccount(username, saaSAccount))
                .then(updateStorageQuota(username, subscriptionMessage.features()))
                .then(updateRateLimiting(username, subscriptionMessage.features()))
                .doOnSuccess(success -> LOGGER.info("Updated SaaS subscription for user: {}, isPaying: {}, canUpgrade: {}, mail features: {}",
                    username, subscriptionMessage.isPaying(), subscriptionMessage.canUpgrade(), subscriptionMessage.features().mail())));
    }

    private Mono<Void> updateStorageQuota(Username username, SaasFeatures saasFeatures) {
        QuotaRoot quotaRoot = userQuotaRootResolver.forUser(username);

        return saasFeatures.mail()
            .map(SaasFeatures.MailLimitation::storageQuota)
            .map(SaaSSubscriptionUtils::asQuotaSizeLimit)
            .map(quota -> maxQuotaManager.setMaxStorageReactive(quotaRoot, quota))
            .map(Mono::from)
            .orElse(Mono.empty());
    }

    private Mono<Void> updateRateLimiting(Username username, SaasFeatures saasFeatures) {
        return saasFeatures.mail()
            .map(SaasFeatures.MailLimitation::rateLimitingDefinition)
            .map(rateLimiting -> rateLimitingRepository.setRateLimiting(username, rateLimiting))
            .map(Mono::from)
            .orElse(Mono.empty());
    }

}
