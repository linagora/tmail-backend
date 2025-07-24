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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.utils.MimeMessageHeadersUtil;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.mime.MessageContentExtractor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
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
    private static final int MAX_ADDRESSES_STRING_LENGTH = 512;
    private static final String DELETED_MESSAGE_PLACEHOLDER = "_This message was deleted._\n\n";
    private static final String MESSAGE_SEPARATOR = "\n";

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
                            .flatMap(messageResult -> {
                                ThreadId threadId = messageResult.getThreadId();
                                Headers headers = messageResult.getHeaders();
                                MimeMessageId currentNode = MessageIdMapper.parseMimeMessageId(headers).get();
                                Optional<MimeMessageId> parentOpt = MessageIdMapper.parseInReplyTo(headers);

                                List<MessageResult> threadMessages = getThreadMessagesWithHeaders(threadId, session);
                                MessageIdMapper mapper = new MessageIdMapper(threadMessages);

                                return ThreadTree.loadFromStorage(threadId)
                                    .flatMap(result -> {
                                        ThreadTree tree;
                                        if (result == null) {
                                            tree = new ThreadTree(threadId, threadMessages);
                                        } else {
                                            tree = result;
                                            parentOpt.ifPresent(parent -> tree.addAndTryLink(parent, currentNode));
                                        }
                                        List<MimeMessageId> branch = tree.getBranchOf(currentNode);
                                        HashMap<MimeMessageId, MessageResult> nodeToMessage = getBranchAccessibleMessagesWithBodies(branch, mapper, session);

                                        String content = "";
                                        for (MimeMessageId node : branch) {
                                            if (nodeToMessage.containsKey(node)) {
                                                assert !tree.isMarkedDeleted(node);
                                                MessageResult message = nodeToMessage.get(node);
                                                content += asRagLearnableContent(message);
                                                content += MESSAGE_SEPARATOR;
                                            } else {
                                                tree.markDeleted(node);
                                                // TODO: for consecutive deleted messages, add ...{N} deleted messages...
                                                content += DELETED_MESSAGE_PLACEHOLDER;
                                            }
                                        }
                                        LOGGER.info("RAG Listener successfully processed document ***** \n{}\n *****", content);

                                        return openRagHttpClient.addDocument(
                                                partitionFactory.forUsername(addedEvent.getUsername()),
                                                new DocumentId(threadId, branch.get(0)),
                                                content,
                                                getMetaData(addedEvent, messageResult)
                                            )
                                            .then(tree.saveToStorage());
                                    })
                                    .then();
                                });
                    });
            }
        } else {
            LOGGER.info("RAG Listener skipped for user: {}", event.getUsername().getLocalPart());
        }
        return Mono.empty();
    }

    private Map<String, String> getMetaData(MailboxEvents.Added addedEvent, MessageResult messageResult) {
        try {
            System.out.println("date: " + DateTimeFormatter.ISO_INSTANT.format(parseMessage(messageResult.getFullContent().getInputStream()).getDate().toInstant()));
            return  Map.of("date", DateTimeFormatter.ISO_INSTANT.format(parseMessage(messageResult.getFullContent().getInputStream()).getDate().toInstant()),
                "doctype", "com.linagora.email");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // NOTE: are we reinventing the wheel?
    // TODO: return Mono instead?
    private List<MessageResult> getThreadMessagesWithHeaders(ThreadId threadId, MailboxSession session) {
        List<MessageId> thread = new ArrayList<>();
        // Add all of the messages to the thread.
        mailboxManager.getThread(threadId, session).subscribe(new Subscriber() {
            void onNext(MessageId message) {
                thread.add(message);
            }
            void onError(java.lang.Throwable error) {
                throw error;
            }
            void onComplete() {}
        });
        // FetchGroup(EnumSet.of(Profile.MIME_HEADERS)) ?
        return messageIdManager.getMessages(thread, FetchGroup.HEADERS, session);
    }

    private HashMap<MimeMessageId, MessageResult> getBranchAccessibleMessagesWithBodies(List<MimeMessageId> branch, MessageIdMapper mapper, MailboxSession session) {
        List<MessageId> messageIds = new ArrayList<>();
        for (MimeMessageId node : branch) {
            messageIds.add(mapper.toMessageId(node));
        }
        Set<MessageId> accessibleNodes = messageIdManager.accessibleMessages(messageIds, session);
        List<MessageResult> accessibleMessages = messageIdManager.getMessages(messageIds, FetchGroup.FULL_CONTENT, session);
        HashMap<MimeMessageId, MessageResult> nodeToMessage = new HashMap<>();
        for (MessageResult message : accessibleMessages) {
            nodeToMessage.put(mapper.toMimeMessageId(message.getMessageId()), message);
        }
        return nodeToMessage;
    }


    private String asRagLearnableContent(MessageResult messageResult) {
        Message mimeMessage = parseMessage(messageResult.getFullContent().getInputStream());
        StringBuilder markdownBuilder = new StringBuilder();
        markdownBuilder.append("# Email Headers\n\n");
        markdownBuilder.append(mimeMessage.getSubject() != null ? "Subject: " + mimeMessage.getSubject().trim() : "");
        markdownBuilder.append(mimeMessage.getFrom() != null ? "\nFrom: " + mimeMessage.getFrom().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")) : "");
        markdownBuilder.append(toBoundedAddressesString(mimeMessage.getTo(), "\nTo: "));
        markdownBuilder.append(toBoundedAddressesString(mimeMessage.getCc(), "\nCc: "));
        markdownBuilder.append("\nDate: " + mimeMessage.getDate()
            .toInstant()
            .atZone(ZoneId.of("UTC"))
            .format(RFC822_DATE_FORMAT));
        List<String> attachmentNames = findAttachmentNames(mimeMessage);
        if (!attachmentNames.isEmpty()) {
            markdownBuilder.append("\nAttachments: " + String.join(", ", attachmentNames));
        }
        markdownBuilder.append("\n\n# Email Content\n\n");
        String content = new MessageContentExtractor().extract(mimeMessage).getTextBody().get();
        // TODO: get the HTML content instead?
        markdownBuilder.append(stripQuotesAndSignature(content));
        return markdownBuilder.toString();
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

    private String toBoundedAddressesString(AddressList addresses, String prefix) {
        if (addresses == null) {
            return "";
        }
        String s = prefix + addresses.stream()
            .map(Object::toString)
            .collect(Collectors.joining(", "));
        if (s.length() < MAX_ADDRESSES_STRING_LENGTH) {
            return s;
        }
        return s.substring(0, MAX_ADDRESSES_STRING_LENGTH) + "...";
    }

    private String stripQuotesAndSignature(String emailContent) {
        // TODO
        return emailContent;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

}
