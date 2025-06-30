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
package com.linagora.tmail.mailet.rag;

import java.util.Set;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.mime.MessageContentExtractor;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RagListener implements EventListener.ReactiveEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagListener.class);

    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final Set<Username> whitelist;

    @Inject
    public RagListener(MailboxManager mailboxManager, MessageIdManager messageIdManager, SystemMailboxesProvider systemMailboxesProvider, Set<Username> whitelist) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.whitelist = whitelist;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxEvents.Added addedEvent && addedEvent.isAppended() || event instanceof MailboxEvents.Expunged;
    }


    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (isUserAllowed(event.getUsername())) {
            if (event instanceof MailboxEvents.Added addedEvent && addedEvent.isAppended()) {
                return Flux.concat(
                        systemMailboxesProvider.getMailboxByRole(Role.SPAM, addedEvent.getUsername()),
                        systemMailboxesProvider.getMailboxByRole(Role.TRASH, addedEvent.getUsername()))
                    .map(MessageManager::getId)
                    .any(mailboxId -> mailboxId.equals(addedEvent.getMailboxId()))
                    .flatMap(isSpamOrTrash -> {
                        if (isSpamOrTrash) {
                            return Mono.empty();
                        }
                        LOGGER.info("RAG Listener triggered for mailbox: {}", addedEvent.getMailboxId());
                        MailboxSession session = mailboxManager.createSystemSession(addedEvent.getUsername());
                    return Mono.from(messageIdManager.getMessagesReactive(addedEvent.getMessageIds(), FetchGroup.FULL_CONTENT, session))
                            .doOnError(error -> System.err.println("Error occurred: " + error.getMessage()))
                            .flatMap(this::extractEmailContent)
                            .doOnNext(text -> LOGGER.info("RAG Listener successfully processed mailContent ***** {} *****", text))
                            .then();
                    });
            }
            if (event instanceof MailboxEvents.Expunged) {
                MailboxEvents.Expunged deletedEvent = (MailboxEvents.Expunged) event;
                LOGGER.info("RAG Listener triggered for mailbox deletion: {}", deletedEvent.getMailboxId());
            }
        } else {
            LOGGER.info("RAG Listener skipped for user: {}", event.getUsername().getLocalPart());
        }
        return Mono.empty();
    }

    private Mono<String> extractEmailContent(MessageResult messageResult) {
        return Mono.fromCallable(() -> {
            DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
            messageBuilder.setMimeEntityConfig(MimeConfig.DEFAULT);
            Message mimeMessage = messageBuilder.parseMessage(messageResult.getFullContent().getInputStream());
            MessageContentExtractor.MessageContent extractor = new MessageContentExtractor().extract(mimeMessage);
            return extractor.getTextBody();
        }).handle(ReactorUtils.publishIfPresent());
    }

    public boolean isUserAllowed(Username userEmail) {
        return whitelist.contains(userEmail);
    }
}
