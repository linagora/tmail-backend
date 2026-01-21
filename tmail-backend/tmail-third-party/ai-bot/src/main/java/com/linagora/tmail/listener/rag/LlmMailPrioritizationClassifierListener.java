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

package com.linagora.tmail.listener.rag;

import static com.linagora.tmail.event.TmailEventModule.TMAIL_EVENT_BUS_INJECT_NAME;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.events.RegistrationKey;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableSet;
import com.google.inject.name.Named;
import com.linagora.tmail.james.jmap.settings.JmapSettings;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.listener.rag.event.AIAnalysisNeeded;
import com.linagora.tmail.listener.rag.filter.MessageFilter;
import com.linagora.tmail.listener.rag.filter.MessageFilterParser;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LlmMailPrioritizationClassifierListener implements EventListener.ReactiveGroupEventListener {
    record ParsedMessage(MessageResult messageResult, Message parsed) {
        static ParsedMessage from(MessageResult messageResult) {
            try (InputStream inputStream = messageResult.getFullContent().getInputStream()) {
                DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
                messageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
                messageBuilder.setDecodeMonitor(DecodeMonitor.SILENT);
                Message mimeMessage = messageBuilder.parseMessage(inputStream);
                return new ParsedMessage(messageResult, mimeMessage);
            } catch (IOException | MailboxException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class LlmMailPrioritizationClassifierGroup extends Group {

    }

    public static final Group GROUP = new LlmMailPrioritizationClassifierGroup();
    private static final Set<RegistrationKey> NO_REGISTRATION_KEYS = ImmutableSet.of();

    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;
    private final JmapSettingsRepository jmapSettingsRepository;
    private final EventBus tmailEventBus;
    private final MessageFilter messageFilter;

    @Inject
    public LlmMailPrioritizationClassifierListener(MailboxManager mailboxManager,
                                                   MessageIdManager messageIdManager,
                                                   SystemMailboxesProvider systemMailboxesProvider,
                                                   JmapSettingsRepository jmapSettingsRepository,
                                                   @Named(TMAIL_EVENT_BUS_INJECT_NAME) EventBus tmailEventBus,
                                                   HierarchicalConfiguration<ImmutableNode> configuration) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.jmapSettingsRepository = jmapSettingsRepository;
        this.tmailEventBus = tmailEventBus;
        this.messageFilter = new MessageFilterParser(systemMailboxesProvider).parse(configuration);
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxEvents.Added added && added.isDelivery();
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxEvents.Added added && added.isDelivery()) {
            return processAddedEvent(added);
        }

        return Mono.empty();
    }

    private Mono<Void> processAddedEvent(MailboxEvents.Added addedEvent) {
        Username username = addedEvent.getUsername();
        MailboxSession session = mailboxManager.createSystemSession(username);

        return aiNeedActionsSettingEnabled(username)
            .flatMap(any -> dispatchAiAnalysisIfNeeded(addedEvent, session));
    }

    private Mono<Void> dispatchAiAnalysisIfNeeded(MailboxEvents.Added addedEvent, MailboxSession session) {
        return Flux.from(messageIdManager.getMessagesReactive(addedEvent.getMessageIds(), FetchGroup.FULL_CONTENT, session))
            .map(ParsedMessage::from)
            .filterWhen(message -> messageFilter.matches(new MessageFilter.FilterContext(addedEvent, message.messageResult(), message.parsed(), session)))
            .flatMap(messageResult -> dispatchAiAnalysisNeeded(messageResult, session), ReactorUtils.LOW_CONCURRENCY)
            .doFinally(signal -> mailboxManager.endProcessingRequest(session))
            .then();
    }

    private Mono<Void> dispatchAiAnalysisNeeded(ParsedMessage message, MailboxSession session) {
        return tmailEventBus.dispatch(asAiAnalysisNeededEvent(message, session), NO_REGISTRATION_KEYS);
    }

    private AIAnalysisNeeded asAiAnalysisNeededEvent(ParsedMessage message, MailboxSession session) {
        return new AIAnalysisNeeded(Event.EventId.random(), session.getUser(),
            message.messageResult().getMailboxId(), message.messageResult().getMessageId());
    }

    private Mono<Boolean> aiNeedActionsSettingEnabled(Username username) {
        return Mono.from(jmapSettingsRepository.get(username))
            .map(JmapSettings::aiNeedsActionEnable)
            .defaultIfEmpty(JmapSettings.AI_NEEDS_ACTION_DISABLE_DEFAULT_VALUE())
            .filter(Boolean::booleanValue);
    }
}
