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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

/**
 * This class is copied & adapted from {@link org.apache.james.jmap.event.ComputeMessageFastViewProjectionListener}
 */

package com.linagora.tmail.event;

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import jakarta.inject.Inject;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.model.MessageId;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class ExpungeMessageFastViewProjectionListener implements EventListener.ReactiveGroupEventListener {
    public static class ExpungeMessageFastViewProjectionListenerGroup extends Group {

    }

    private static final Group GROUP = new ExpungeMessageFastViewProjectionListenerGroup();
    private final MessageIdManager messageIdManager;
    private final MessageFastViewProjection messageFastViewProjection;
    private final SessionProvider sessionProvider;

    @Inject
    public ExpungeMessageFastViewProjectionListener(SessionProvider sessionProvider, MessageIdManager messageIdManager,
                                                    MessageFastViewProjection messageFastViewProjection) {
        this.sessionProvider = sessionProvider;
        this.messageIdManager = messageIdManager;
        this.messageFastViewProjection = messageFastViewProjection;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public Mono<Void> reactiveEvent(Event event) {
        if (event instanceof Expunged expunged) {
            MailboxSession session = sessionProvider.createSystemSession(event.getUsername());
            return handleExpungedEvent(expunged, session);
        }
        return Mono.empty();
    }


    @Override
    public boolean isHandling(Event event) {
        return event instanceof Expunged;
    }

    private Mono<Void> handleExpungedEvent(Expunged expunged, MailboxSession session) {
        ImmutableSet<MessageId> expungedMessageIds = expunged.getMessageIds();
        return Mono.from(messageIdManager.accessibleMessagesReactive(expungedMessageIds, session))
            .flatMapIterable(accessibleMessageIds -> CollectionUtils.subtract(expungedMessageIds, accessibleMessageIds))
            .flatMap(messageFastViewProjection::delete, DEFAULT_CONCURRENCY)
            .then();
    }

}
