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

package com.linagora.tmail.encrypted;

import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.utils.JsoupHtmlTextExtractor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.store.mail.model.impl.MessageParserImpl;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.mime.MessageContentExtractor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.pgp.Decrypter;

import reactor.core.publisher.Mono;

public class InMemoryEncryptedMessageManagerTest {
    private EncryptedMessageManager testee;
    private MailboxManager mailboxManager;
    private MessageManager messageManager;
    private KeystoreManager keystoreManager;
    private MailboxSession session;
    private MailboxPath path;
    private Message message;
    private InMemoryEncryptedEmailContentStore emailContentStore;
    private DeDuplicationBlobStore blobStore;

    @BeforeAll
    static void setUpAll() throws Exception {
        String bouncyCastleProviderClassName = "org.bouncycastle.jce.provider.BouncyCastleProvider";
        Security.addProvider((Provider) Class.forName(bouncyCastleProviderClassName).getDeclaredConstructor().newInstance());
    }

    @BeforeEach
    void setUp() throws Exception {
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        session = mailboxManager.createSystemSession(BOB);
        path = MailboxPath.inbox(session);
        MailboxId mailboxId = mailboxManager.createMailbox(path, session).get();
        messageManager = mailboxManager.getMailbox(mailboxId, session);

        keystoreManager = new InMemoryKeystoreManager();
        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
        blobStore = new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), new PlainBlobId.Factory());
        emailContentStore = new InMemoryEncryptedEmailContentStore(blobStore);
        testee = new EncryptedMessageManager(messageManager, keystoreManager,
            new ClearEmailContentFactory(new MessageParserImpl(), messageContentExtractor, new Preview.Factory(messageContentExtractor, new JsoupHtmlTextExtractor())),
            emailContentStore);

        message = Message.Builder
            .of()
            .setSubject("test")
            .setSender(BOB.asString())
            .setFrom(BOB.asString())
            .setTo(ALICE.asString())
            .setBody("testmail", StandardCharsets.UTF_8)
            .build();
    }

    @Test
    void commandAppendShouldEncryptMessage() throws Exception {
        byte[] keyBytes = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        Mono.from(keystoreManager.save(BOB, keyBytes)).block();

        testee.appendMessage(MessageManager.AppendCommand.from(message), session);

        MessageResultIterator messages = mailboxManager.getMailbox(path, session)
            .getMessages(MessageRange.all(), FetchGroup.BODY_CONTENT, session);
        MessageResult result = messages.next();

        assertThat(new String(result.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
            .doesNotContain("testmail");
    }

    @Test
    void commandAppendShouldStoreEncryptedFastViewContent() throws Exception {
        byte[] keyBytes = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        Mono.from(keystoreManager.save(BOB, keyBytes)).block();

        MessageManager.AppendResult appendResult = testee.appendMessage(MessageManager.AppendCommand.from(message), session);

        EncryptedEmailFastView fastView = Mono.from(emailContentStore.retrieveFastView(appendResult.getId().getMessageId())).block();

        byte[] decryptedPayload = Decrypter
            .forKey(ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.private"), "123456".toCharArray())
            .decrypt(new ByteArrayInputStream(fastView.encryptedPreview().getBytes(StandardCharsets.UTF_8)))
            .readAllBytes();

        assertThat(new String(decryptedPayload, StandardCharsets.UTF_8))
            .contains("testmail");
    }

    @Test
    void commandAppendShouldStoreEncryptedContent() throws Exception {
        byte[] keyBytes = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        Mono.from(keystoreManager.save(BOB, keyBytes)).block();

        MessageManager.AppendResult appendResult = testee.appendMessage(MessageManager.AppendCommand.from(message), session);

        EncryptedEmailDetailedView fastView = Mono.from(emailContentStore.retrieveDetailedView(appendResult.getId().getMessageId())).block();

        byte[] decryptedPayload = Decrypter
            .forKey(ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.private"), "123456".toCharArray())
            .decrypt(new ByteArrayInputStream(fastView.encryptedHtml().getBytes(StandardCharsets.UTF_8)))
            .readAllBytes();

        assertThat(new String(decryptedPayload, StandardCharsets.UTF_8))
            .contains("testmail");
    }

    @Test
    void commandAppendShouldStoreEncryptedAttachments() throws Exception {
        byte[] keyBytes = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        Mono.from(keystoreManager.save(BOB, keyBytes)).block();

        MessageManager.AppendResult appendResult = testee.appendMessage(MessageManager.AppendCommand
            .from(ClassLoaderUtils.getSystemResourceAsSharedStream("emailWithTextAttachment.eml")), session);

        BlobId encryptedAttachmentBlobId = Mono.from(emailContentStore.retrieveAttachmentContent(appendResult.getId().getMessageId(), 0)).block();
        byte[] encryptedAttachment = Mono.from(blobStore.readBytes(BucketName.DEFAULT, encryptedAttachmentBlobId)).block();

        byte[] decryptedPayload = Decrypter
            .forKey(ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.private"), "123456".toCharArray())
            .decrypt(new ByteArrayInputStream(encryptedAttachment))
            .readAllBytes();

        assertThat(new String(decryptedPayload, StandardCharsets.UTF_8))
            .contains("This is a beautiful banana.");
    }

    @Test
    void commandAppendShouldEncryptMessageWithMultipleKeys() throws Exception {
        byte[] keyBytes1 = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        byte[] keyBytes2 = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg2.pub").readAllBytes();
        Mono.from(keystoreManager.save(BOB, keyBytes1)).block();
        Mono.from(keystoreManager.save(BOB, keyBytes2)).block();

        testee.appendMessage(MessageManager.AppendCommand.from(message), session);

        MessageResultIterator messages = mailboxManager.getMailbox(path, session)
            .getMessages(MessageRange.all(), FetchGroup.BODY_CONTENT, session);
        MessageResult result = messages.next();

        assertThat(new String(result.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
            .doesNotContain("testmail");
    }

    @Test
    void commandAppendedWithSingleKeyShouldBeDecryptable() throws Exception {
        byte[] keyBytes = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        Mono.from(keystoreManager.save(BOB, keyBytes)).block();

        testee.appendMessage(MessageManager.AppendCommand.from(message), session);

        MessageResultIterator messages = mailboxManager.getMailbox(path, session)
            .getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session);
        MessageResult result = messages.next();

        Body body = new DefaultMessageBuilder().parseMessage(result.getFullContent().getInputStream()).getBody();
        Multipart encryptedMultiPart = (Multipart) body;
        Body encryptedBodyPart = encryptedMultiPart.getBodyParts().get(1).getBody();
        ByteArrayOutputStream encryptedBodyBytes = new ByteArrayOutputStream();
        new DefaultMessageWriter().writeBody(encryptedBodyPart, encryptedBodyBytes);

        byte[] decryptedPayload = Decrypter
            .forKey(ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.private"), "123456".toCharArray())
            .decrypt(new ByteArrayInputStream(encryptedBodyBytes.toByteArray()))
            .readAllBytes();

        assertThat(new String(decryptedPayload, StandardCharsets.UTF_8))
            .contains("testmail");
    }

    @Test
    void commandAppendedWithMultipleKeysShouldBeDecryptable() throws Exception {
        byte[] keyBytes1 = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        byte[] keyBytes2 = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg2.pub").readAllBytes();
        Mono.from(keystoreManager.save(BOB, keyBytes1)).block();
        Mono.from(keystoreManager.save(BOB, keyBytes2)).block();

        testee.appendMessage(MessageManager.AppendCommand.from(message), session);

        MessageResultIterator messages = mailboxManager.getMailbox(path, session)
            .getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session);
        MessageResult result = messages.next();

        Body body = new DefaultMessageBuilder().parseMessage(result.getFullContent().getInputStream()).getBody();
        Multipart encryptedMultiPart = (Multipart) body;
        Body encryptedBodyPart = encryptedMultiPart.getBodyParts().get(1).getBody();
        ByteArrayOutputStream encryptedBodyBytes = new ByteArrayOutputStream();
        new DefaultMessageWriter().writeBody(encryptedBodyPart, encryptedBodyBytes);

        byte[] decryptedPayload = Decrypter
            .forKey(ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.private"), "123456".toCharArray())
            .decrypt(new ByteArrayInputStream(encryptedBodyBytes.toByteArray()))
            .readAllBytes();

        assertThat(new String(decryptedPayload, StandardCharsets.UTF_8))
            .contains("testmail");
    }

    @Test
    void commandAppendShouldThrowWhenNoPublicKey() throws Exception {
        testee.appendMessage(MessageManager.AppendCommand.from(message), session);

        MessageResultIterator messages = mailboxManager.getMailbox(path, session)
            .getMessages(MessageRange.all(), FetchGroup.BODY_CONTENT, session);
        MessageResult result = messages.next();

        assertThat(new String(result.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
            .contains("testmail");
    }

    @Test
    void commandAppendShouldNotEncryptWhenNoPublicKey() throws Exception {
        testee.appendMessage(MessageManager.AppendCommand.from(message), session);

        MessageResultIterator messages = mailboxManager.getMailbox(path, session)
            .getMessages(MessageRange.all(), FetchGroup.BODY_CONTENT, session);
        MessageResult result = messages.next();

        assertThat(new String(result.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
            .contains("testmail");
    }

    @Test
    void commandAppendShouldNotEncryptWhenNameSpaceIsNotPrivate() throws Exception {
        byte[] keyBytes = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        Mono.from(keystoreManager.save(BOB, keyBytes)).block();

        MailboxPath path = new MailboxPath("#TeamMailbox", BOB, "marketing");
        MailboxId mailboxId = mailboxManager.createMailbox(path, session).get();
        messageManager = mailboxManager.getMailbox(mailboxId, session);

        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
        EncryptedMessageManager testee = new EncryptedMessageManager(messageManager, keystoreManager,
            new ClearEmailContentFactory(new MessageParserImpl(), messageContentExtractor, new Preview.Factory(messageContentExtractor, new JsoupHtmlTextExtractor())),
            emailContentStore);

        testee.appendMessage(MessageManager.AppendCommand.from(message), session);

        MessageResultIterator messages = mailboxManager.getMailbox(path, session)
            .getMessages(MessageRange.all(), FetchGroup.BODY_CONTENT, session);
        MessageResult result = messages.next();

        assertThat(new String(result.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
            .contains("testmail");
    }

    @Test
    void commandAppendShouldNotEncryptWhenMessageEncrypted() throws Exception {
        byte[] keyBytes = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        Mono.from(keystoreManager.save(BOB, keyBytes)).block();

        MessageManager.AppendCommand messageAppend = MessageManager.AppendCommand
            .from(ClassLoaderUtils.getSystemResourceAsSharedStream("emailEncrypted.eml"));

        testee.appendMessage(messageAppend, session);

        MessageResultIterator messages = mailboxManager.getMailbox(path, session)
            .getMessages(MessageRange.all(), FetchGroup.BODY_CONTENT, session);
        MessageResult result = messages.next();

        assertThat(new String(result.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
            .contains("content email 123");
    }
}
