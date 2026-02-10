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

package com.linagora.tmail.listener;

import static org.apache.james.util.ReactorUtils.LOW_CONCURRENCY;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.mail.Flags;
import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.james.jmap.contact.ContactAddIndexingProcessor;
import com.linagora.tmail.james.jmap.contact.ContactFields;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CollectTrustedContactsListener implements EventListener.ReactiveGroupEventListener {
    public static class CollectTrustedContactsListenerGroup extends Group {

    }

    public static final String TO_BE_COLLECTED_FLAG = "$to_be_collected";
    private static final Group GROUP = new CollectTrustedContactsListenerGroup();
    private static final Logger LOGGER = LoggerFactory.getLogger(CollectTrustedContactsListener.class);

    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;
    private final ContactAddIndexingProcessor contactAddIndexingProcessor;

    @Inject
    public CollectTrustedContactsListener(MailboxManager mailboxManager,
                                          MessageIdManager messageIdManager,
                                          ContactAddIndexingProcessor contactAddIndexingProcessor) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.contactAddIndexingProcessor = contactAddIndexingProcessor;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        if (event instanceof MailboxEvents.Added addedEvent) {
            return !addedEvent.isMoved() && hasTrustedFlag(addedEvent);
        }
        if (event instanceof MailboxEvents.FlagsUpdated flagsUpdatedEvent) {
            return isTrustFlagTransition(flagsUpdatedEvent);
        }
        return false;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxEvents.Added addedEvent) {
            return collectContacts(addedEvent.getUsername(), messageIds(addedEvent));
        }
        if (event instanceof MailboxEvents.FlagsUpdated flagsUpdatedEvent) {
            return collectContacts(flagsUpdatedEvent.getUsername(), messageIds(flagsUpdatedEvent));
        }
        return Mono.empty();
    }

    private List<MessageId> messageIds(MailboxEvents.Added addedEvent) {
        return addedEvent.getAdded().values().stream()
            .filter(metadata -> hasTrustedFlag(metadata.getFlags()))
            .map(MessageMetaData::getMessageId)
            .toList();
    }

    private List<MessageId> messageIds(MailboxEvents.FlagsUpdated flagsUpdatedEvent) {
        return flagsUpdatedEvent.getUpdatedFlags().stream()
            .filter(this::isTrustFlagTransition)
            .map(UpdatedFlags::getMessageId)
            .flatMap(Optional::stream)
            .toList();
    }

    private Mono<Void> collectContacts(Username username, List<MessageId> messageIds) {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);

        return Flux.from(messageIdManager.getMessagesReactive(messageIds, FetchGroup.HEADERS, mailboxSession))
            .flatMap(messageResult -> extractContacts(messageResult, username))
            .flatMap(contact -> Mono.from(contactAddIndexingProcessor.process(username, contact)), LOW_CONCURRENCY)
            .then();
    }

    private boolean hasTrustedFlag(MailboxEvents.Added addedEvent) {
        return addedEvent.getAdded()
            .values()
            .stream()
            .map(MessageMetaData::getFlags)
            .anyMatch(this::hasTrustedFlag);
    }

    private boolean isTrustFlagTransition(MailboxEvents.FlagsUpdated flagsUpdatedEvent) {
        return flagsUpdatedEvent.getUpdatedFlags()
            .stream()
            .anyMatch(this::isTrustFlagTransition);
    }

    private boolean isTrustFlagTransition(UpdatedFlags updatedFlags) {
        return !hasTrustedFlag(updatedFlags.getOldFlags())
            && hasTrustedFlag(updatedFlags.getNewFlags());
    }

    private boolean hasTrustedFlag(Flags flags) {
        return flags.contains(Flags.Flag.FLAGGED) || flags.contains(TO_BE_COLLECTED_FLAG);
    }

    private Flux<ContactFields> extractContacts(MessageResult messageResult, Username username) {
        return parseMessage(messageResult)
            .flatMapMany(parsedMessage -> Flux.fromStream(allMailboxes(parsedMessage))
                .flatMap(mailbox -> Mono.justOrEmpty(asContact(mailbox, username))))
            .filter(contact -> !isEventUser(contact, username));
    }

    private Mono<Message> parseMessage(MessageResult messageResult) {
        return Mono.fromCallable(() -> {
            try (InputStream inputStream = messageResult.getFullContent().getInputStream()) {
                DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
                messageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
                messageBuilder.setDecodeMonitor(DecodeMonitor.SILENT);
                return messageBuilder.parseMessage(inputStream);
            } catch (IOException | MailboxException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Stream<Mailbox> allMailboxes(Message message) {
        Stream<Mailbox> from = Optional.ofNullable(message.getFrom())
            .stream()
            .flatMap(Collection::stream);

        Stream<Mailbox> recipients = Stream.of(message.getTo(), message.getCc())
            .filter(Objects::nonNull)
            .map(AddressList::flatten)
            .flatMap(Collection::stream);

        return Stream.concat(from, recipients);
    }

    private Optional<ContactFields> asContact(Mailbox mailbox, Username username) {
        String address = mailbox.getAddress();
        try {
            MailAddress mailAddress = new MailAddress(address);
            return Optional.of(ContactFields.of(mailAddress, Optional.ofNullable(mailbox.getName()).orElse("")));
        } catch (AddressException e) {
            LOGGER.info("Skipping malformed address {} while collecting trusted contacts for {}", address, username.asString());
            return Optional.empty();
        }
    }

    private boolean isEventUser(ContactFields contactFields, Username username) {
        return contactFields.address().asString().equalsIgnoreCase(username.asString());
    }
}
