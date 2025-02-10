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

package com.linagora.tmail.event;

import java.io.IOException;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.opensearch.events.OpenSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.store.ResultUtils;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ComputePreviewMessageIndexer implements OpenSearchListeningMessageSearchIndex.Indexer {
    private final MessageFastViewProjection messageFastViewProjection;
    private final MessageFastViewPrecomputedProperties.Factory messageFastViewPrecomputedPropertiesFactory;

    @Inject
    public ComputePreviewMessageIndexer(MessageFastViewProjection messageFastViewProjection,
                                        MessageFastViewPrecomputedProperties.Factory messageFastViewPrecomputedPropertiesFactory) {
        this.messageFastViewProjection = messageFastViewProjection;
        this.messageFastViewPrecomputedPropertiesFactory = messageFastViewPrecomputedPropertiesFactory;
    }

    @Override
    public Mono<Void> added(MailboxSession session, MailboxEvents.Added addedEvent, Mailbox mailbox, MailboxMessage message) {
        if (!addedEvent.isAppended()) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> ResultUtils.loadMessageResult(message, FetchGroup.FULL_CONTENT))
            .subscribeOn(Schedulers.parallel())
            .flatMap(Throwing.function(messageResult -> Mono.fromCallable(() -> Pair.of(message.getMessageId(),
                    computeFastViewPrecomputedProperties(messageResult)))
                .subscribeOn(Schedulers.parallel())))
            .flatMap(messageIdToPreview -> Mono.from(messageFastViewProjection.store(messageIdToPreview.getKey(), messageIdToPreview.getValue())))
            .then();
    }

    @VisibleForTesting
    MessageFastViewPrecomputedProperties computeFastViewPrecomputedProperties(MessageResult messageResult) throws MailboxException, IOException {
        return messageFastViewPrecomputedPropertiesFactory.from(messageResult);
    }
}
