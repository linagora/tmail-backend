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

import static org.apache.mailet.base.DateFormats.RFC822_DATE_FORMAT;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.jmap.mime4j.AvoidBinaryBodyReadingBodyFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.mime.MessageContentExtractor;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.linagora.tmail.mailet.rag.httpclient.DocumentId;
import com.linagora.tmail.mailet.rag.httpclient.OpenRagHttpClient;
import com.linagora.tmail.mailet.rag.httpclient.Partition;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public class RagListener implements EventListener.ReactiveGroupEventListener {
    public static class RagListenerGroup extends Group {

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RagListener.class);
    private static final Group GROUP = new RagListenerGroup();

    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final Optional<List<Username>> whitelist;
    private final RagConfig ragConfig;
    private final Partition.Factory partitionFactory;
    private final OpenRagHttpClient openRagHttpClient;

    @Inject
    public RagListener(MailboxManager mailboxManager, MessageIdManager messageIdManager, SystemMailboxesProvider systemMailboxesProvider, HierarchicalConfiguration<ImmutableNode> config, RagConfig ragConfig) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.whitelist = parseWhitelist(config);
        this.ragConfig = ragConfig;
        this.partitionFactory = Partition.Factory.fromPattern(ragConfig.getPartitionPattern());
        this.openRagHttpClient = new OpenRagHttpClient(ragConfig);
    }

    private Optional<List<Username>> parseWhitelist(HierarchicalConfiguration<ImmutableNode> config) {
        String users = config.getString("listener.configuration.users", null);
        if (users == null || users.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(List.of(users.split(","))
            .stream()
            .map(String::trim)
            .filter(user -> !user.isEmpty())
            .map(Username::of)
            .collect(Collectors.toList()));
    }

    public Optional<List<Username>> getWhitelist() {
        return whitelist;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return ReactiveGroupEventListener.super.getExecutionMode();
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxEvents.Added addedEvent && addedEvent.isAppended();
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
                            .flatMap(messageResult ->
                                addDocumentToRagContext(addedEvent, messageResult))
                            .then();
                    });
            }
        } else {
            LOGGER.info("RAG Listener skipped for user: {}", event.getUsername().getLocalPart());
        }
        return Mono.empty();
    }

    private Mono<String> addDocumentToRagContext(MailboxEvents.Added addedEvent, MessageResult messageResult) {
        return asRagLearnableContent(messageResult)
            .doOnSuccess(text -> LOGGER.debug("RAG Listener successfully processed mailContent ***** \n{}\n *****", new DocumentId(messageResult.getThreadId())))
            .flatMap(content -> openRagHttpClient
                .addDocument(
                    partitionFactory.forUsername(addedEvent.getUsername()),
                    new DocumentId(messageResult.getThreadId()),
                    content,
                    computeMetaData(addedEvent, messageResult)));
    }

    private Map<String, String> computeMetaData(MailboxEvents.Added addedEvent, MessageResult messageResult) {
        try {
            return  Map.of("date", DateTimeFormatter.ISO_INSTANT.format(parseMessage(messageResult.getFullContent().getInputStream()).getDate().toInstant()),
                "doctype", "com.linagora.email");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Mono<String> asRagLearnableContent(MessageResult messageResult) {
        return Mono.fromCallable(() -> {
            Message mimeMessage = parseMessage(messageResult.getFullContent().getInputStream());
            StringBuilder markdownBuilder = new StringBuilder();
            markdownBuilder.append("# Email Headers\n\n");
            markdownBuilder.append(mimeMessage.getSubject() != null ? "Subject: " + mimeMessage.getSubject().trim() : "");
            markdownBuilder.append(mimeMessage.getFrom() != null ? "\nFrom: " + mimeMessage.getFrom().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")) : "");
            markdownBuilder.append(mimeMessage.getTo() != null ? "\nTo: " + mimeMessage.getTo().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")) : "");
            markdownBuilder.append(mimeMessage.getCc() != null ? "\nCc: " + mimeMessage.getCc().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")) : "");
            markdownBuilder.append("\nDate: " + mimeMessage.getDate()
                .toInstant()
                .atZone(ZoneId.of("UTC"))
                .format(RFC822_DATE_FORMAT));
            List<String> attachmentNames = findAttachmentNames(mimeMessage);
            if (!attachmentNames.isEmpty()) {
                markdownBuilder.append("\nAttachments: " + String.join(", ", attachmentNames));
            }
            markdownBuilder.append("\n\n# Email Content\n\n");
            markdownBuilder.append(new MessageContentExtractor().extract(mimeMessage).getTextBody().get());
            return markdownBuilder.toString();
        });
    }

    private Message parseMessage(InputStream inputStream) throws IOException {
        DefaultMessageBuilder defaultMessageBuilder = new DefaultMessageBuilder();
        defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        defaultMessageBuilder.setBodyFactory(new AvoidBinaryBodyReadingBodyFactory());
        return defaultMessageBuilder.parseMessage(inputStream);
    }

    public boolean isUserAllowed(Username userEmail) {
        return this.whitelist.isEmpty() || this.whitelist.get().contains(userEmail);
    }

    private List<String> findAttachmentNames(Entity entity) {
        List<String> attachmentNames = new ArrayList<>();
        Body body = entity.getBody();

        if (body instanceof Multipart) {
            Multipart multipart = (Multipart) body;
            for (Entity part : multipart.getBodyParts()) {
                attachmentNames.addAll(findAttachmentNames(part));
            }
        } else {
            String dispositionType = entity.getDispositionType();
            if ("attachment".equalsIgnoreCase(dispositionType)) {
                String fileName = entity.getFilename();
                if (fileName != null && !fileName.trim().isEmpty()) {
                    attachmentNames.add(fileName);
                }
            }
        }
        return attachmentNames;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

}
