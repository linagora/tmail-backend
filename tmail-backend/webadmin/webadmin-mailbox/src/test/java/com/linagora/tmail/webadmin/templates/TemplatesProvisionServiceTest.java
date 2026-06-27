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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.task.Task;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;

import jakarta.mail.Flags;
import reactor.core.publisher.Flux;

class TemplatesProvisionServiceTest {
    private static final Domain DOMAIN = Domain.of("example.org");
    private static final Username ALICE = Username.fromLocalPartWithDomain("alice", DOMAIN);
    private static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    private static final Username CEDRIC = Username.fromLocalPartWithDomain("cedric", DOMAIN);
    private static final String TEMPLATES = DefaultMailboxes.TEMPLATES;

    private TemplatesProvisionService service;
    private StoreMailboxManager mailboxManager;
    private SessionProvider sessionProvider;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        sessionProvider = mailboxManager.getSessionProvider();

        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(DOMAIN);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(ALICE, "anyPassword");
        usersRepository.addUser(BOB, "anyPassword");
        usersRepository.addUser(CEDRIC, "anyPassword");

        service = new TemplatesProvisionService(mailboxManager, usersRepository);
    }

    @Test
    void provisionDomainShouldCopyTemplatesToUsersWithoutTemplatesFolder() {
        createTemplatesFolder(ALICE);
        appendTemplate(ALICE, "<t1@example.org>", "First template");
        appendTemplate(ALICE, "<t2@example.org>", "Second template");

        TemplatesProvisionContext context = new TemplatesProvisionContext();
        Task.Result result = service.provisionDomain(DOMAIN, new TemplatingSource(ALICE, TEMPLATES), ProvisionOptions.DEFAULT, 10, context).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(messageIds(BOB)).containsExactlyInAnyOrder("<t1@example.org>", "<t2@example.org>");
        assertThat(messageIds(CEDRIC)).containsExactlyInAnyOrder("<t1@example.org>", "<t2@example.org>");
    }

    @Test
    void provisionDomainShouldNotCopyTemplatesToSourceUser() {
        createTemplatesFolder(ALICE);
        appendTemplate(ALICE, "<t1@example.org>", "First template");

        TemplatesProvisionContext context = new TemplatesProvisionContext();
        service.provisionDomain(DOMAIN, new TemplatingSource(ALICE, TEMPLATES), ProvisionOptions.DEFAULT, 10, context).block();

        assertThat(messageIds(ALICE)).containsExactly("<t1@example.org>");
        assertThat(context.snapshot().processedUsers()).isEqualTo(2);
    }

    @Test
    void provisionDomainShouldSkipTemplatesAlreadyPresentByMessageId() {
        createTemplatesFolder(ALICE);
        appendTemplate(ALICE, "<t1@example.org>", "Source body");
        appendTemplate(ALICE, "<t2@example.org>", "Second template");
        createTemplatesFolder(CEDRIC);
        appendTemplate(CEDRIC, "<t1@example.org>", "Cedric existing body");

        TemplatesProvisionContext context = new TemplatesProvisionContext();
        service.provisionDomain(DOMAIN, new TemplatingSource(ALICE, TEMPLATES), ProvisionOptions.DEFAULT, 10, context).block();

        assertThat(messageIds(CEDRIC)).containsExactlyInAnyOrder("<t1@example.org>", "<t2@example.org>");
        assertThat(bodies(CEDRIC)).anyMatch(body -> body.contains("Cedric existing body"));
        assertThat(context.snapshot().skippedTemplates()).isEqualTo(1);
    }

    @Test
    void provisionDomainShouldReplaceExistingTemplateWhenOverwriteExisting() {
        createTemplatesFolder(ALICE);
        appendTemplate(ALICE, "<t1@example.org>", "Source body");
        createTemplatesFolder(CEDRIC);
        appendTemplate(CEDRIC, "<t1@example.org>", "Cedric existing body");

        TemplatesProvisionContext context = new TemplatesProvisionContext();
        service.provisionDomain(DOMAIN, new TemplatingSource(ALICE, TEMPLATES), new ProvisionOptions(true, false), 10, context).block();

        assertThat(messageIds(CEDRIC)).containsExactly("<t1@example.org>");
        assertThat(bodies(CEDRIC)).allMatch(body -> body.contains("Source body"));
    }

    @Test
    void provisionDomainShouldRemoveOrphanTemplatesWhenPrune() {
        createTemplatesFolder(ALICE);
        appendTemplate(ALICE, "<t1@example.org>", "First template");
        createTemplatesFolder(CEDRIC);
        appendTemplate(CEDRIC, "<orphan@example.org>", "Old template removed from source");

        TemplatesProvisionContext context = new TemplatesProvisionContext();
        service.provisionDomain(DOMAIN, new TemplatingSource(ALICE, TEMPLATES), new ProvisionOptions(false, true), 10, context).block();

        assertThat(messageIds(CEDRIC)).containsExactly("<t1@example.org>");
        assertThat(context.snapshot().removedTemplates()).isEqualTo(1);
    }

    @Test
    void provisionDomainShouldKeepOrphanTemplatesWhenPruneDisabled() {
        createTemplatesFolder(ALICE);
        appendTemplate(ALICE, "<t1@example.org>", "First template");
        createTemplatesFolder(CEDRIC);
        appendTemplate(CEDRIC, "<orphan@example.org>", "Old template");

        TemplatesProvisionContext context = new TemplatesProvisionContext();
        service.provisionDomain(DOMAIN, new TemplatingSource(ALICE, TEMPLATES), ProvisionOptions.DEFAULT, 10, context).block();

        assertThat(messageIds(CEDRIC)).containsExactlyInAnyOrder("<t1@example.org>", "<orphan@example.org>");
    }

    @Test
    void provisionUserShouldCopyTemplatesToTargetUser() {
        createTemplatesFolder(ALICE);
        appendTemplate(ALICE, "<t1@example.org>", "First template");
        appendTemplate(ALICE, "<t2@example.org>", "Second template");

        TemplatesProvisionContext context = new TemplatesProvisionContext();
        Task.Result result = service.provisionUser(new TemplatingSource(ALICE, TEMPLATES), BOB, ProvisionOptions.DEFAULT, context).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(messageIds(BOB)).containsExactlyInAnyOrder("<t1@example.org>", "<t2@example.org>");
        assertThat(context.snapshot().processedUsers()).isEqualTo(1);
    }

    @Test
    void provisionShouldFailWhenSourceFolderDoesNotExist() {
        TemplatesProvisionContext context = new TemplatesProvisionContext();
        assertThatThrownBy(() -> service.provisionUser(new TemplatingSource(ALICE, TEMPLATES), BOB, ProvisionOptions.DEFAULT, context).block())
            .isInstanceOf(SourceTemplatesFolderNotFoundException.class);
    }

    @Test
    void provisionShouldSupportCustomFolderName() {
        String folderName = "Templates.Marketing";
        createFolder(ALICE, folderName);
        appendTemplate(ALICE, folderName, "<t1@example.org>", "Marketing template");

        TemplatesProvisionContext context = new TemplatesProvisionContext();
        service.provisionUser(new TemplatingSource(ALICE, folderName), BOB, ProvisionOptions.DEFAULT, context).block();

        assertThat(messageIds(BOB, folderName)).containsExactly("<t1@example.org>");
    }

    @Test
    void sourceFolderExistsShouldReturnFalseWhenMissing() {
        assertThat(service.sourceFolderExists(new TemplatingSource(ALICE, TEMPLATES)).block()).isFalse();
    }

    @Test
    void sourceFolderExistsShouldReturnTrueWhenPresent() {
        createTemplatesFolder(ALICE);
        assertThat(service.sourceFolderExists(new TemplatingSource(ALICE, TEMPLATES)).block()).isTrue();
    }

    private void createTemplatesFolder(Username user) {
        createFolder(user, TEMPLATES);
    }

    private void createFolder(Username user, String folderName) {
        try {
            MailboxSession session = sessionProvider.createSystemSession(user);
            mailboxManager.createMailbox(MailboxPath.forUser(user, folderName), session);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void appendTemplate(Username user, String messageId, String body) {
        appendTemplate(user, TEMPLATES, messageId, body);
    }

    private void appendTemplate(Username user, String folderName, String messageId, String body) {
        try {
            MailboxSession session = sessionProvider.createSystemSession(user);
            MessageManager messageManager = mailboxManager.getMailbox(MailboxPath.forUser(user, folderName), session);
            String eml = "Message-ID: " + messageId + "\r\nSubject: A template\r\n\r\n" + body;
            messageManager.appendMessage(new ByteArrayInputStream(eml.getBytes(StandardCharsets.UTF_8)),
                new Date(), session, false, new Flags());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> messageIds(Username user) {
        return messageIds(user, TEMPLATES);
    }

    private List<String> messageIds(Username user, String folderName) {
        return readFullContents(user, folderName).stream()
            .map(this::extractMessageId)
            .collect(ImmutableList.toImmutableList());
    }

    private List<String> bodies(Username user) {
        return readFullContents(user, TEMPLATES);
    }

    private List<String> readFullContents(Username user, String folderName) {
        try {
            MailboxSession session = sessionProvider.createSystemSession(user);
            MessageManager messageManager = mailboxManager.getMailbox(MailboxPath.forUser(user, folderName), session);
            return Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.FULL_CONTENT, session))
                .map(messageResult -> {
                    try (InputStream inputStream = messageResult.getFullContent().getInputStream()) {
                        return new String(ByteStreams.toByteArray(inputStream), StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(ImmutableList.toImmutableList())
                .block();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String extractMessageId(String eml) {
        return eml.lines()
            .filter(line -> line.toLowerCase().startsWith("message-id:"))
            .map(line -> line.substring("message-id:".length()).trim())
            .findFirst()
            .orElse(null);
    }
}
