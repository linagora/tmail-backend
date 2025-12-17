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
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.mime4j.AvoidBinaryBodyReadingBodyFactory;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.ThreadIdGuessingAlgorithm;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.Field;
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
import reactor.core.scheduler.Schedulers;


public class RagListener implements EventListener.ReactiveGroupEventListener {
    public static class RagListenerGroup extends Group {

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RagListener.class);
    private static final Group GROUP = new RagListenerGroup();

    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm;
    private final Optional<List<Username>> whitelist;
    private final Partition.Factory partitionFactory;
    private final OpenRagHttpClient openRagHttpClient;

    @Inject
    public RagListener(MailboxManager mailboxManager, MessageIdManager messageIdManager, SystemMailboxesProvider systemMailboxesProvider,
                       ThreadIdGuessingAlgorithm threadIdGuessingAlgorithm, HierarchicalConfiguration<ImmutableNode> config,
                       Partition.Factory partitionFactory, OpenRagHttpClient openRagHttpClient) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.threadIdGuessingAlgorithm = threadIdGuessingAlgorithm;
        this.whitelist = parseWhitelist(config);
        this.partitionFactory = partitionFactory;
        this.openRagHttpClient = openRagHttpClient;
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
                                addDocumentToRagContext(addedEvent, messageResult, session))
                            .then();
                    });
            }
        } else {
            LOGGER.info("RAG Listener skipped for user: {}", event.getUsername().getLocalPart());
        }
        return Mono.empty();
    }

    private Mono<String> addDocumentToRagContext(MailboxEvents.Added addedEvent, MessageResult messageResult, MailboxSession session) {
        return asRagLearnableContent(messageResult)
            .doOnSuccess(text -> LOGGER.debug("RAG Listener successfully processed mailContent ***** \n{}\n *****", new DocumentId(messageResult.getMessageId())))
            .flatMap(content -> computeMetaData(addedEvent, messageResult, session)
                .flatMap(metaData ->
                    openRagHttpClient.addDocument(
                    partitionFactory.forUsername(addedEvent.getUsername()),
                    new DocumentId(messageResult.getMessageId()),
                    content,
                    metaData)));
    }

    private Mono<Map<String, String>> computeMetaData(MailboxEvents.Added addedEvent, MessageResult messageResult, MailboxSession session) {
        try {
            Message mimeMessage = parseMessage(messageResult.getFullContent().getInputStream());
            String text = new MessageContentExtractor()
                .extract(mimeMessage)
                .getTextBody()
                .orElse("");

            String subject = Optional.ofNullable(mimeMessage.getSubject()).orElse("");
            String datetime = mimeMessage.getDate() == null
                ? ""
                : DateTimeFormatter.ISO_INSTANT.format(mimeMessage.getDate().toInstant());

            return mimeMessageIdToMailboxMessageId(messageResult.getThreadId(), getInReplyTo(mimeMessage), session)
                .map(MessageId::serialize)
                .defaultIfEmpty("")
                .publishOn(Schedulers.parallel())
                .map(parentId -> Map.of(
                    "email.subject", subject,
                    "datetime", datetime,
                    "parent_id", parentId,
                    "relationship_id", messageResult.getThreadId().serialize(),
                    "doctype", "com.linagora.email",
                    "email.preview", Preview.compute(text).getValue()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Mono<MessageId> mimeMessageIdToMailboxMessageId(ThreadId threadId, String mimeMessageID, MailboxSession session) {
        if (mimeMessageID.isEmpty()) {
            return Mono.empty();
        }
        return threadIdGuessingAlgorithm.getMessageIdsInThread(threadId, session)
            .collectList()
            .flatMapMany(messageIds -> messageIdManager.getMessagesReactive(messageIds, FetchGroup.HEADERS, session))
            .filter(message -> messageMatchesInReplyTo(message, mimeMessageID))
            .next()
            .map(MessageResult::getMessageId);
    }

    private boolean messageMatchesInReplyTo(MessageResult message, String expectedValue) {
        try {
            Message mimeMessage = parseMessage(message.getFullContent().getInputStream());
            Field field = mimeMessage.getHeader().getField("Message-ID");
            String messageId = field == null ? "" : stripSurroundingAngleBrackets(field.getBody().trim());
            return messageId.equals(expectedValue);
        } catch (IOException | MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    private String getInReplyTo(Message mimeMessage) {
        String inReplyTo = mimeMessage.getHeader().getField("In-Reply-To") == null ? "" : mimeMessage.getHeader().getField("In-Reply-To")
            .getBody()
            .trim();
        return stripSurroundingAngleBrackets(inReplyTo);
    }

    private String stripSurroundingAngleBrackets(String raw) {
        if (raw.length() > 0 && raw.charAt(0) == '<') {
            raw = raw.substring(1);
        }
        if (raw.length() > 0 && raw.charAt(raw.length() - 1) == '>') {
            raw = raw.substring(0, raw.length() - 1);
        }
        return raw;
    }

    private Mono<String> asRagLearnableContent(MessageResult messageResult) {
        return Mono.fromCallable(() -> {
            Message mimeMessage = parseMessage(messageResult.getFullContent().getInputStream());
            StringBuilder markdownBuilder = new StringBuilder();
            LatestEmailReplyExtractor replyExtractor = new LatestEmailReplyExtractor.RegexBased();
            markdownBuilder.append("# Email Headers\n\n");
            markdownBuilder.append(mimeMessage.getSubject() != null ? "Subject: " + mimeMessage.getSubject().trim() : "Subject: ");
            markdownBuilder.append(mimeMessage.getFrom() != null ? "\nFrom: " + mimeMessage.getFrom().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")) : "");
            markdownBuilder.append(mimeMessage.getTo() != null ? "\nTo: " + mimeMessage.getTo().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")) : "");
            markdownBuilder.append(mimeMessage.getCc() != null ? "\nCc: " + mimeMessage.getCc().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")) : "");
            if (mimeMessage.getDate() != null) {
                markdownBuilder.append("\nDate: " + mimeMessage.getDate()
                    .toInstant()
                    .atZone(ZoneId.of("UTC"))
                    .format(RFC822_DATE_FORMAT));
            } else {
                markdownBuilder.append("\nDate: ");
            }
            List<String> attachmentNames = findAttachmentNames(mimeMessage);
            if (!attachmentNames.isEmpty()) {
                markdownBuilder.append("\nAttachments: " + String.join(", ", attachmentNames));
            }
            markdownBuilder.append("\n\n# Email Content\n\n");
            markdownBuilder.append(new MessageContentExtractor()
                .extract(mimeMessage)
                .getTextBody()
                .map(replyExtractor::cleanQuotedContent)
                .orElse(""));
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
