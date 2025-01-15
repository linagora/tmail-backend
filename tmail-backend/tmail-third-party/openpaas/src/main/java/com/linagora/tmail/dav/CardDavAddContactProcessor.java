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
 ********************************************************************/

package com.linagora.tmail.carddav;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.james.core.Username;
import org.apache.james.util.FunctionalUtils;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.james.jmap.contact.ContactAddIndexingProcessor;
import com.linagora.tmail.james.jmap.contact.ContactFields;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CardDavAddContactProcessor implements ContactAddIndexingProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardDavAddContactProcessor.class);

    private final CardDavClient cardDavClient;
    private final AsyncLoadingCache<Username, String> openPassUserIdLoader;

    @Inject
    @Singleton
    public CardDavAddContactProcessor(CardDavClient cardDavClient,
                                      OpenPaasRestClient openPaasRestClient) {
        this.cardDavClient = cardDavClient;

        AsyncCacheLoader<Username, String> openPaasUserIdCacheLoader = (key, executor) -> openPaasRestClient.searchOpenPaasUserId(key.asString())
            .subscribeOn(Schedulers.fromExecutor(executor))
            .toFuture();

        this.openPassUserIdLoader = Caffeine.newBuilder()
            .expireAfterWrite(20, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .buildAsync(openPaasUserIdCacheLoader);
    }

    @Override
    public Publisher<Void> process(Username username, ContactFields contactFields) {
        return Mono.fromFuture(openPassUserIdLoader.get(username))
            .flatMap(openPassUserId -> {
                CardDavCreationObjectRequest cardDavCreationObjectRequest = CardDavCreationFactory.create(Optional.of(contactFields.fullName()), contactFields.address());
                return cardDavClient.existsCollectedContact(username.asString(), openPassUserId, cardDavCreationObjectRequest.uid())
                    .filter(FunctionalUtils.identityPredicate().negate())
                    .flatMap(exists -> cardDavClient.createCollectedContact(username.asString(), openPassUserId, cardDavCreationObjectRequest))
                    .onErrorResume(error -> {
                        LOGGER.error("Error while creating collected contact if not exists.", error);
                        return Mono.empty();
                    });
            });
    }
}
