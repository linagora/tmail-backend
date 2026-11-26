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

package com.linagora.tmail.space;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.reactivestreams.Publisher;

import com.linagora.tmail.space.SpaceAnnotations.SpaceBinding;
import com.linagora.tmail.space.SpaceMailboxes.SpaceMailbox;
import com.linagora.tmail.space.SpacePublisher.Direction;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxNameSpace;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

/**
 * Reports the mail of a space's team mailbox to the space feed. Only mailboxes bound to a live space report: their
 * provisioned event was published once the binding was written, so TwakeSpace already knows the container. A failed
 * publish goes to the event dead letters, and is redelivered with the same event id.
 */
public class SpaceActivityListener implements EventListener.ReactiveGroupEventListener {
    public static class SpaceActivityListenerGroup extends Group {

    }

    private static final Group GROUP = new SpaceActivityListenerGroup();

    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;
    private final SpaceMailboxes spaceMailboxes;
    private final SpacePublisher publisher;
    private final Clock clock;

    @Inject
    public SpaceActivityListener(MailboxManager mailboxManager, MessageIdManager messageIdManager, SpaceMailboxes spaceMailboxes,
                                 SpacePublisher publisher, Clock clock) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.spaceMailboxes = spaceMailboxes;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxEvents.Added addedEvent
            && addedEvent.getMailboxPath().getNamespace().equals(TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE());
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxEvents.Added addedEvent) {
            return OptionConverters.toJava(TeamMailbox.from(addedEvent.getMailboxPath()))
                .flatMap(teamMailbox -> direction(addedEvent, teamMailbox)
                    .map(direction -> report(addedEvent, teamMailbox, direction)))
                .orElse(Mono.empty());
        }
        return Mono.empty();
    }

    private Optional<Direction> direction(MailboxEvents.Added addedEvent, TeamMailbox teamMailbox) {
        if (addedEvent.getMailboxPath().equals(teamMailbox.sentPath())) {
            return Optional.of(Direction.SENT);
        }
        if (addedEvent.isDelivery()) {
            return Optional.of(Direction.RECEIVED);
        }
        return Optional.empty();
    }

    private Mono<Void> report(MailboxEvents.Added addedEvent, TeamMailbox teamMailbox, Direction direction) {
        Instant time = clock.instant();
        return spaceMailboxes.read(teamMailbox)
            .filter(SpaceMailbox::isLive)
            .flatMap(spaceMailbox -> Flux.from(messageIdManager.getMessagesReactive(addedEvent.getMessageIds(), FetchGroup.HEADERS,
                    mailboxManager.createSystemSession(teamMailbox.owner())))
                .distinct(MessageResult::getMessageId)
                .concatMap(message -> Mono.fromCallable(() -> subject(message))
                    .flatMap(subject -> publisher.publishMessage(new SpacePublisher.MessageActivity(organizationId(spaceMailbox),
                        spaceMailbox.rootId(), message.getMessageId(), direction, subject, time))))
                .then());
    }

    private String organizationId(SpaceMailbox spaceMailbox) {
        return spaceMailbox.binding().map(SpaceBinding::organizationId).orElseThrow();
    }

    private Optional<String> subject(MessageResult messageResult) throws IOException, MailboxException {
        try (InputStream inputStream = messageResult.getHeaders().getInputStream()) {
            DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
            messageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
            Message headers = messageBuilder.parseMessage(inputStream);
            return Optional.ofNullable(headers.getSubject());
        }
    }
}
