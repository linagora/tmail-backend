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

package com.linagora.tmail.webadmin.templates;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteStreams;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TemplatesProvisionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TemplatesProvisionService.class);

    private record SourceTemplate(Optional<String> messageId, byte[] content) {
    }

    private record TargetTemplate(Optional<String> messageId, MessageUid uid) {
    }

    private final MailboxManager mailboxManager;
    private final UsersRepository usersRepository;

    @Inject
    public TemplatesProvisionService(MailboxManager mailboxManager, UsersRepository usersRepository) {
        this.mailboxManager = mailboxManager;
        this.usersRepository = usersRepository;
    }

    public Mono<Task.Result> provisionDomain(Domain domain, Username sourceUser, String folderName,
                                             ProvisionOptions options, int usersPerSecond, TemplatesProvisionContext context) {
        return loadSourceTemplates(sourceUser, folderName)
            .flatMap(templates -> Flux.from(usersRepository.listUsersOfADomainReactive(domain))
                .filter(user -> !user.equals(sourceUser))
                .transform(ReactorUtils.<Username, Task.Result>throttle()
                    .elements(usersPerSecond)
                    .per(Duration.ofSeconds(1))
                    .forOperation(user -> provisionForSingleUser(templates, user, folderName, options, context)))
                .reduce(Task.Result.COMPLETED, Task::combine)
                .onErrorResume(e -> {
                    LOGGER.error("Error while provisioning templates for users of domain {}", domain.asString(), e);
                    return Mono.just(Task.Result.PARTIAL);
                }));
    }

    public Mono<Task.Result> provisionUser(Username sourceUser, Username targetUser, String folderName,
                                           ProvisionOptions options, TemplatesProvisionContext context) {
        return loadSourceTemplates(sourceUser, folderName)
            .flatMap(templates -> provisionForSingleUser(templates, targetUser, folderName, options, context));
    }

    public Mono<Boolean> sourceFolderExists(Username sourceUser, String folderName) {
        MailboxSession session = mailboxManager.createSystemSession(sourceUser);
        return Mono.from(mailboxManager.getMailboxReactive(MailboxPath.forUser(sourceUser, folderName), session))
            .map(any -> true)
            .onErrorResume(MailboxNotFoundException.class, e -> Mono.just(false));
    }

    private Mono<ImmutableList<SourceTemplate>> loadSourceTemplates(Username sourceUser, String folderName) {
        MailboxSession session = mailboxManager.createSystemSession(sourceUser);
        return Mono.from(mailboxManager.getMailboxReactive(MailboxPath.forUser(sourceUser, folderName), session))
            .onErrorResume(MailboxNotFoundException.class,
                e -> Mono.error(new SourceTemplatesFolderNotFoundException(sourceUser, folderName)))
            .flatMapMany(messageManager -> Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.FULL_CONTENT, session)))
            .map(Throwing.function(this::toSourceTemplate))
            .collect(ImmutableList.toImmutableList());
    }

    private SourceTemplate toSourceTemplate(MessageResult messageResult) throws Exception {
        byte[] content;
        try (InputStream inputStream = messageResult.getFullContent().getInputStream()) {
            content = ByteStreams.toByteArray(inputStream);
        }
        return new SourceTemplate(parseMessageId(new ByteArrayInputStream(content)), content);
    }

    private Mono<Task.Result> provisionForSingleUser(List<SourceTemplate> templates, Username targetUser, String folderName,
                                                     ProvisionOptions options, TemplatesProvisionContext context) {
        MailboxSession session = mailboxManager.createSystemSession(targetUser);
        MailboxPath targetPath = MailboxPath.forUser(targetUser, folderName);
        return ensureMailbox(session, targetPath)
            .then(Mono.from(mailboxManager.getMailboxReactive(targetPath, session)))
            .flatMap(messageManager -> applyTemplates(templates, messageManager, session, options, context))
            .then(Mono.fromCallable(() -> {
                context.incrementProcessedUsers();
                LOGGER.info("Templates folder '{}' provisioned for user {}", folderName, targetUser.asString());
                return Task.Result.COMPLETED;
            }))
            .onErrorResume(e -> {
                LOGGER.error("Error while provisioning templates folder '{}' for user {}", folderName, targetUser.asString(), e);
                context.addToFailedUsers(targetUser.asString());
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private Mono<Void> applyTemplates(List<SourceTemplate> templates, MessageManager messageManager, MailboxSession session,
                                      ProvisionOptions options, TemplatesProvisionContext context) {
        return Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.HEADERS, session))
            .map(Throwing.function(this::toTargetTemplate))
            .collectList()
            .flatMap(targets -> {
                ListMultimap<String, MessageUid> targetsByMessageId = ArrayListMultimap.create();
                targets.forEach(target -> target.messageId().ifPresent(id -> targetsByMessageId.put(id, target.uid())));

                return Flux.fromIterable(templates)
                    .concatMap(template -> applyTemplate(template, messageManager, session, options, context, targetsByMessageId))
                    .then(Mono.defer(() -> pruneTemplates(templates, messageManager, session, options, context, targetsByMessageId)));
            });
    }

    private Mono<Void> applyTemplate(SourceTemplate template, MessageManager messageManager, MailboxSession session,
                                     ProvisionOptions options, TemplatesProvisionContext context,
                                     ListMultimap<String, MessageUid> targetsByMessageId) {
        Optional<String> messageId = template.messageId();
        boolean alreadyPresent = messageId.isPresent() && targetsByMessageId.containsKey(messageId.get());

        if (alreadyPresent && !options.overwriteExisting()) {
            context.incrementSkippedTemplates();
            return Mono.empty();
        }
        Mono<Void> deleteExisting = alreadyPresent
            ? messageManager.deleteReactive(ImmutableList.copyOf(targetsByMessageId.get(messageId.get())), session).then()
            : Mono.empty();
        return deleteExisting
            .then(appendTemplate(template, messageManager, session))
            .doOnSuccess(any -> context.incrementAppliedTemplates());
    }

    private Mono<Void> pruneTemplates(List<SourceTemplate> templates, MessageManager messageManager, MailboxSession session,
                                      ProvisionOptions options, TemplatesProvisionContext context,
                                      ListMultimap<String, MessageUid> targetsByMessageId) {
        if (!options.prune()) {
            return Mono.empty();
        }
        Set<String> sourceMessageIds = templates.stream()
            .map(SourceTemplate::messageId)
            .flatMap(Optional::stream)
            .collect(ImmutableSet.toImmutableSet());
        ImmutableList<MessageUid> orphans = targetsByMessageId.entries().stream()
            .filter(entry -> !sourceMessageIds.contains(entry.getKey()))
            .map(java.util.Map.Entry::getValue)
            .collect(ImmutableList.toImmutableList());
        if (orphans.isEmpty()) {
            return Mono.empty();
        }
        return messageManager.deleteReactive(orphans, session)
            .doOnSuccess(any -> context.incrementRemovedTemplates(orphans.size()))
            .then();
    }

    private Mono<Void> appendTemplate(SourceTemplate template, MessageManager messageManager, MailboxSession session) {
        return Mono.fromCallable(() -> AppendCommand.builder().build(parseMessage(template.content())))
            .flatMap(appendCommand -> Mono.from(messageManager.appendMessageReactive(appendCommand, session)))
            .then();
    }

    private TargetTemplate toTargetTemplate(MessageResult messageResult) throws Exception {
        try (InputStream inputStream = messageResult.getHeaders().getInputStream()) {
            return new TargetTemplate(parseMessageId(inputStream), messageResult.getUid());
        }
    }

    private Mono<Void> ensureMailbox(MailboxSession session, MailboxPath mailboxPath) {
        return Mono.from(mailboxManager.createMailboxReactive(mailboxPath, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, session))
            .onErrorResume(MailboxExistsException.class, e -> Mono.empty())
            .then();
    }

    private Optional<String> parseMessageId(InputStream inputStream) {
        try {
            return Optional.ofNullable(messageBuilder().parseMessage(inputStream).getMessageId());
        } catch (Exception e) {
            LOGGER.warn("Failed to parse the Message-Id of a template, it will not be deduplicated", e);
            return Optional.empty();
        }
    }

    private Message parseMessage(byte[] content) throws Exception {
        return messageBuilder().parseMessage(new ByteArrayInputStream(content));
    }

    private DefaultMessageBuilder messageBuilder() {
        DefaultMessageBuilder builder = new DefaultMessageBuilder();
        builder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        builder.setDecodeMonitor(DecodeMonitor.SILENT);
        return builder;
    }
}
