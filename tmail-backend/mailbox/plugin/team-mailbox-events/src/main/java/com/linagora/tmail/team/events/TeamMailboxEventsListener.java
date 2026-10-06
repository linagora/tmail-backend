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

package com.linagora.tmail.team.events;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.reactivestreams.Publisher;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxNameSpace;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

public class TeamMailboxEventsListener implements EventListener.ReactiveGroupEventListener {
    public static class TeamMailboxEventsListenerGroup extends Group {

    }

    private static final Group GROUP = new TeamMailboxEventsListenerGroup();

    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;
    private final Clock clock;
    private final RabbitMQTeamMailboxEventPublisher publisher;

    @Inject
    public TeamMailboxEventsListener(MailboxManager mailboxManager, MessageIdManager messageIdManager, Clock clock,
                                     RabbitMQConfiguration rabbitMQConfiguration, HierarchicalConfiguration<ImmutableNode> configuration) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.clock = clock;
        this.publisher = RabbitMQTeamMailboxEventPublisher.create(TeamMailboxEventsConfiguration.from(configuration), rabbitMQConfiguration);
    }

    @PreDestroy
    public void close() {
        publisher.close();
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
                    .map(direction -> publish(addedEvent, teamMailbox, direction)))
                .orElse(Mono.empty());
        }
        return Mono.empty();
    }

    private Optional<TeamMailboxEvent.Direction> direction(MailboxEvents.Added addedEvent, TeamMailbox teamMailbox) {
        if (addedEvent.getMailboxPath().equals(teamMailbox.sentPath())) {
            return Optional.of(TeamMailboxEvent.Direction.SENT);
        }
        if (addedEvent.isDelivery()) {
            return Optional.of(TeamMailboxEvent.Direction.RECEIVED);
        }
        return Optional.empty();
    }

    private Mono<Void> publish(MailboxEvents.Added addedEvent, TeamMailbox teamMailbox, TeamMailboxEvent.Direction direction) {
        MailboxSession session = mailboxManager.createSystemSession(teamMailbox.owner());
        Flux<TeamMailboxEvent> events = Flux.from(messageIdManager.getMessagesReactive(addedEvent.getMessageIds(), FetchGroup.HEADERS, session))
            .distinct(MessageResult::getMessageId)
            .concatMap(messageResult -> Mono.fromCallable(() -> toEvent(addedEvent, teamMailbox, direction, messageResult)));
        return publisher.publish(events);
    }

    private TeamMailboxEvent toEvent(MailboxEvents.Added addedEvent, TeamMailbox teamMailbox, TeamMailboxEvent.Direction direction,
                                     MessageResult messageResult) throws IOException, MailboxException {
        Message headers = parseHeaders(messageResult);
        return new TeamMailboxEvent(
            teamMailbox,
            direction,
            addedEvent.getMailboxPath(),
            addedEvent.getMailboxId(),
            messageResult.getMessageId(),
            Optional.ofNullable(headers.getSubject()),
            Optional.ofNullable(headers.getFrom()).map(this::addresses),
            Optional.ofNullable(headers.getTo()).map(AddressList::flatten).map(this::addresses),
            Optional.ofNullable(headers.getCc()).map(AddressList::flatten).map(this::addresses),
            Optional.ofNullable(headers.getBcc()).map(AddressList::flatten).map(this::addresses),
            Optional.ofNullable(headers.getDate()).map(Date::toInstant),
            clock.instant());
    }

    private List<TeamMailboxEvent.EmailAddress> addresses(MailboxList mailboxes) {
        return mailboxes.stream()
            .map(mailbox -> new TeamMailboxEvent.EmailAddress(Optional.ofNullable(mailbox.getName()), mailbox.getAddress()))
            .toList();
    }

    private Message parseHeaders(MessageResult messageResult) throws IOException, MailboxException {
        try (InputStream inputStream = messageResult.getHeaders().getInputStream()) {
            DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
            messageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
            return messageBuilder.parseMessage(inputStream);
        }
    }
}
