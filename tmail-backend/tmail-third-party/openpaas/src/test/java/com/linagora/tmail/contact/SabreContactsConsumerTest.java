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

package com.linagora.tmail.contact;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.metrics.api.NoopGaugeRegistry;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.DockerOpenPaasExtension;
import com.linagora.tmail.DockerOpenPaasSetup;
import com.linagora.tmail.HttpUtils;
import com.linagora.tmail.OpenPaasUser;
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.dav.CardDavUtils;
import com.linagora.tmail.dav.DavClient;
import com.linagora.tmail.dav.DavClientException;
import com.linagora.tmail.dav.request.CardDavCreationObjectRequest;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngine;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Sender;

public class SabreContactsConsumerTest {
    private final ConditionFactory calmlyAwait = Awaitility.with()
        .pollInterval(Duration.ofMillis(500))
        .and()
        .with()
        .pollDelay(Duration.ofMillis(500))
        .await();
    private final ConditionFactory awaitAtMost = calmlyAwait.atMost(200, TimeUnit.SECONDS);

    @RegisterExtension
    static DockerOpenPaasExtension dockerOpenPaasExtension = new DockerOpenPaasExtension(DockerOpenPaasSetup.SINGLETON);

    private DavClient davClient;
    private HttpClient davHTTPClient;
    private EmailAddressContactSearchEngine emailAddressContactSearchEngine;
    private static ReactorRabbitMQChannelPool channelPool;
    private static SimpleConnectionPool connectionPool;
    private static RabbitMQConfiguration rabbitMQConfiguration;
    private static OpenPaasUser openPaasUser;

    @BeforeAll
    static void beforeAll(DockerOpenPaasSetup dockerOpenPaasSetup) throws Exception {
        rabbitMQConfiguration = RabbitMQConfiguration.builder()
            .amqpUri(dockerOpenPaasSetup.rabbitMqUri())
            .managementUri(dockerOpenPaasSetup.rabbitMqManagementUri())
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .maxRetries(3)
            .minDelayInMs(10)
            .connectionTimeoutInMs(100)
            .channelRpcTimeoutInMs(100)
            .handshakeTimeoutInMs(100)
            .shutdownTimeoutInMs(100)
            .networkRecoveryIntervalInMs(100)
            .build();

        RabbitMQConnectionFactory connectionFactory = new RabbitMQConnectionFactory(rabbitMQConfiguration);
        connectionPool = new SimpleConnectionPool(connectionFactory,
            SimpleConnectionPool.Configuration.builder()
                .retries(2)
                .initialDelay(Duration.ofMillis(5)));
        channelPool = new ReactorRabbitMQChannelPool(connectionPool.getResilientConnection(),
            ReactorRabbitMQChannelPool.Configuration.builder()
                .retries(2)
                .maxBorrowDelay(Duration.ofMillis(250))
                .maxChannel(10),
            new RecordingMetricFactory(),
            new NoopGaugeRegistry());
        channelPool.start();
    }

    @AfterAll
    static void afterAll() {
        channelPool.close();
        connectionPool.close();
    }

    @BeforeEach
    void setUp(DockerOpenPaasSetup dockerOpenPaasSetup) throws Exception {
        davClient = new DavClient(dockerOpenPaasExtension.dockerOpenPaasSetup().davConfiguration());
        SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        davHTTPClient = HttpClient.create()
            .baseUrl(dockerOpenPaasExtension.dockerOpenPaasSetup().davConfiguration().baseUrl().toString())
            .responseTimeout(Duration.ofSeconds(2))
            .secure(sslContextSpec -> sslContextSpec.sslContext(sslContext));
        OpenPaasRestClient openPaasRestClient = new OpenPaasRestClient(dockerOpenPaasExtension.dockerOpenPaasSetup().openPaasConfiguration());
        emailAddressContactSearchEngine = new InMemoryEmailAddressContactSearchEngine();
        SabreContactsConsumer consumer = new SabreContactsConsumer(channelPool, emailAddressContactSearchEngine, openPaasRestClient);

        SabreContactsOperator sabreContactsOperator = new SabreContactsOperator(channelPool, rabbitMQConfiguration,
            consumer, dockerOpenPaasSetup.openPaasConfiguration());
        sabreContactsOperator.init();

        openPaasUser = dockerOpenPaasExtension.newTestUser();
    }

    @AfterEach
    void afterEach() {
        Sender sender = channelPool.getSender();
        sender.delete(QueueSpecification.queue().name(SabreContactsConsumer.QUEUE_NAME_ADD)).block();
        sender.delete(QueueSpecification.queue().name(SabreContactsConsumer.QUEUE_NAME_UPDATE)).block();
        sender.delete(QueueSpecification.queue().name(SabreContactsConsumer.QUEUE_NAME_DELETE)).block();
    }

    @Test
    void shouldIndexContactInSearchEngineWhenCollectedContactIsCreated() throws Exception {
        MailAddress mailAddress = new MailAddress("vttran@exmaple.ltd");
        CardDavCreationObjectRequest creationObjectRequest = CardDavUtils.createObjectCreationRequest(Optional.of("Tung Tran"), mailAddress);
        davClient.createCollectedContact(openPaasUser.email(), openPaasUser.id(), creationObjectRequest).block();

        awaitAtMost.untilAsserted(() -> assertThat(Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(),
                "tung", 255)).map(EmailAddressContact::fields)
            .collectList().block())
            .containsExactly(ContactFields.of(mailAddress, "Tung Tran")));
    }

    @Test
    void shouldIndexAllEmailsInSearchEngineWhenCollectedContactHasMultipleEmails() throws Exception {
        // Given: a vCard with multiple email addresses
        String uid = UUID.randomUUID().toString();
        String fullName = "Tung Tran";
        String email1 = "vttran@exmaple.ltd";
        String email2 = "tung.tran@domain.tld";

        String vcard = """
            BEGIN:VCARD
            VERSION:4.0
            FN:%s
            UID:%s
            EMAIL;TYPE=work:%s
            EMAIL;TYPE=home:%s
            END:VCARD
            """.formatted(fullName, uid, email1, email2);

        davClient.putCollectedContact(openPaasUser.email(), openPaasUser.id(), uid, vcard.getBytes(StandardCharsets.UTF_8)).block();

        // Then: both email addresses should be indexed and searchable
        awaitAtMost.untilAsserted(() -> assertThat(
            Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(), "Tung Tran", 255))
                .map(EmailAddressContact::fields)
                .collectList().block()).containsExactlyInAnyOrder(
            ContactFields.of(new MailAddress(email1), fullName),
            ContactFields.of(new MailAddress(email2), fullName)));
    }

    @Test
    void shouldRemoveContactFromSearchEngineWhenCollectedContactIsDeleted() {
        // Given: a contact is created and indexed in the search engine
        CardDavCreationObjectRequest cardDavCreationObjectRequest = createContact("vttran@exmaple.ltd", "Tung Tran");

        // When: the contact is deleted on sabre dav server
        deleteCollectedContact(cardDavCreationObjectRequest.uid());

        // Then: the contact should no longer be present in the search engine
        awaitAtMost.untilAsserted(() -> assertThat(Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(),
                "tung", 255)).map(EmailAddressContact::fields)
            .collectList().block()).isEmpty());
    }

    @Test
    void shouldRemoveAllEmailsFromSearchEngineWhenCollectedContactWithMultipleEmailsIsDeleted() throws Exception {
        // Given: a vCard with multiple email addresses is created and indexed
        String uid = UUID.randomUUID().toString();
        String fullName = "Tung Tran";
        String email1 = "vttran@exmaple.ltd";
        String email2 = "tung.tran@domain.tld";

        String vcard = """
            BEGIN:VCARD
            VERSION:4.0
            FN:%s
            UID:%s
            EMAIL;TYPE=work:%s
            EMAIL;TYPE=home:%s
            END:VCARD
            """.formatted(fullName, uid, email1, email2);

        davClient.putCollectedContact(openPaasUser.email(), openPaasUser.id(), uid, vcard.getBytes(StandardCharsets.UTF_8)).block();

        awaitAtMost.untilAsserted(() -> {
            assertThat(Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(),
                    fullName.toLowerCase(), 255)).map(EmailAddressContact::fields)
                .collectList().block())
                .containsExactlyInAnyOrder(ContactFields.of(Throwing.supplier(() -> new MailAddress(email1)).get(), fullName),
                    ContactFields.of(Throwing.supplier(() -> new MailAddress(email2)).get(), fullName));
        });

        // When: the contact is deleted from sabre dav server
        deleteCollectedContact(uid);

        // Then: all emails should be removed from the search engine
        awaitAtMost.untilAsserted(() -> assertThat(
            Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(), "Tung Tran", 255))
                .map(EmailAddressContact::fields)
                .collectList().block()).isEmpty());
    }

    @Test
    void shouldUpdateContactInSearchEngineWhenFullNameIsChanged() {
        // Given
        CardDavCreationObjectRequest cardDavCreationObjectRequest = createContact("vttran@exmaple.ltd", "Tung Tran");

        // When
        String updatedVcard = cardDavCreationObjectRequest.toVCard()
            .replace("Tung Tran", "Tung Tran Updated");

        davClient.putCollectedContact(openPaasUser.email(), openPaasUser.id(), cardDavCreationObjectRequest.uid(),
            updatedVcard.getBytes(StandardCharsets.UTF_8)).block();

        // Then
        awaitAtMost.untilAsserted(() -> assertThat(Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(),
                "Updated", 255)).map(EmailAddressContact::fields)
            .collectList().block()).containsExactly(ContactFields.of(new MailAddress("vttran@exmaple.ltd"), "Tung Tran Updated")));
    }

    @Test
    void shouldUpdateFullNameForAllEmailsInSearchEngineWhenCollectedContactWithMultipleEmailsIsUpdated() {
        // Given: a contact with multiple emails is created
        String uid = UUID.randomUUID().toString();
        String originalName = "Tung Tran";
        String updatedName = "Tung Tran Updated";
        String email1 = "vttran@exmaple.ltd";
        String email2 = "tung.tran@domain.tld";

        String vcard = """
            BEGIN:VCARD
            VERSION:4.0
            FN:%s
            UID:%s
            EMAIL;TYPE=work:%s
            EMAIL;TYPE=home:%s
            END:VCARD
            """.formatted(originalName, uid, email1, email2);

        davClient.putCollectedContact(openPaasUser.email(), openPaasUser.id(), uid, vcard.getBytes(StandardCharsets.UTF_8)).block();

        awaitAtMost.untilAsserted(() -> {
            assertThat(Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(),
                    originalName.toLowerCase(), 255)).map(EmailAddressContact::fields)
                .collectList().block())
                .containsExactlyInAnyOrder(ContactFields.of(Throwing.supplier(() -> new MailAddress(email1)).get(), originalName),
                    ContactFields.of(Throwing.supplier(() -> new MailAddress(email2)).get(), originalName));
        });

        // When: the full name is updated in the vCard
        String updatedVcard = vcard.replace(originalName, updatedName);

        davClient.putCollectedContact(openPaasUser.email(), openPaasUser.id(), uid, updatedVcard.getBytes(StandardCharsets.UTF_8)).block();

        // Then: all emails should be reindexed with the updated full name
        awaitAtMost.untilAsserted(() -> assertThat(
            Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(), "Tung Tran Updated", 255))
                .map(EmailAddressContact::fields)
                .collectList().block())
            .containsExactlyInAnyOrder(ContactFields.of(new MailAddress(email1), updatedName),
                ContactFields.of(new MailAddress(email2), updatedName)));
    }

    @Test
    void shouldAddNewContactWhenEmailIsChanged() {
        // Given: a contact with the original email is created and indexed
        String vcardUid = UUID.randomUUID().toString();

        String originEmail = "vttran@exmaple.ltd";
        String newEmail = "nobita@domain.tld";

        String vcard = """
            BEGIN:VCARD
            VERSION:4.0
            FN:Tung Tran
            UID:%s
            EMAIL;TYPE=work:%s
            END:VCARD""".formatted(vcardUid, originEmail);

        davClient.putCollectedContact(openPaasUser.email(), openPaasUser.id(), vcardUid, vcard.getBytes(StandardCharsets.UTF_8)).block();
        awaitContactIndexed(originEmail, "Tung Tran");

        // When: the contact is updated to replace the original email with a new one
        String vcardUpdated = vcard.replace(originEmail, newEmail);
        davClient.putCollectedContact(openPaasUser.email(), openPaasUser.id(), vcardUid, vcardUpdated.getBytes(StandardCharsets.UTF_8)).block();

        // Then
        awaitAtMost.untilAsserted(() -> assertThat(Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(),
                newEmail, 255)).map(EmailAddressContact::fields)
            .collectList().block()).containsExactly(ContactFields.of(new MailAddress(newEmail), "Tung Tran")));
    }

    @Nested
    class FailureTest {
        @Test
        void consumeMessageShouldNotCrashOnInvalidMessages() throws Exception {
            channelPool.getSender()
                .send(Mono.just(new OutboundMessage(
                    SabreContactsOperator.EXCHANGE_NAME_ADD,
                    EMPTY_ROUTING_KEY,
                    "BAD_PAYLOAD".getBytes(UTF_8))))
                .block();

            MailAddress mailAddress = new MailAddress("tung@exmaple.ltd");
            CardDavCreationObjectRequest creationObjectRequest = CardDavUtils.createObjectCreationRequest(Optional.of("Tung Tran"), mailAddress);
            davClient.createCollectedContact(openPaasUser.email(), openPaasUser.id(), creationObjectRequest).block();

            awaitAtMost.untilAsserted(() -> assertThat(Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(),
                    "tung", 255)).map(EmailAddressContact::fields)
                .collectList().block())
                .containsExactly(ContactFields.of(mailAddress, "Tung Tran")));
        }

        @Test
        void consumeMessageShouldNotCrashWhenNotExistPrincipalUserId() throws Exception {
            String userId = UUID.randomUUID().toString();
            String amqpMessage = """
                {
                    "path": "addressbooks\\/67e26ebbecd9f300255a9f80\\/contacts\\/3ffe56cc-9196-4266-bf17-4894d00350d4.vcf",
                    "owner": "principals\\/users\\/%s",
                    "carddata": "BEGIN:VCARD\\r\\nVERSION:4.0\\r\\nUID:3ffe56cc-9196-4266-bf17-4894d00350d4\\r\\nFN:Tran Tung\\r\\nN:vwevwe;wf vwe;;;\\r\\nEMAIL;TYPE=Work:mailto:toto@tutu.com\\r\\nEND:VCARD\\r\\n"
                }""".formatted(userId);

            channelPool.getSender()
                .send(Mono.just(new OutboundMessage(
                    SabreContactsOperator.EXCHANGE_NAME_ADD,
                    EMPTY_ROUTING_KEY,
                    amqpMessage.getBytes(UTF_8))))
                .block();

            MailAddress mailAddress = new MailAddress("tung@exmaple.ltd");
            CardDavCreationObjectRequest creationObjectRequest = CardDavUtils.createObjectCreationRequest(Optional.of("Tung Tran"), mailAddress);
            davClient.createCollectedContact(openPaasUser.email(), openPaasUser.id(), creationObjectRequest).block();

            awaitAtMost.untilAsserted(() -> assertThat(Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(),
                    "tung", 255)).map(EmailAddressContact::fields)
                .collectList().block())
                .containsExactly(ContactFields.of(mailAddress, "Tung Tran")));
        }
    }

    @Test
    void shouldHandleContactUpdatedEventWhenSeveralCases() {
        // Given
        String uid = UUID.randomUUID().toString();

        String emailToKeep = "keep@domain.tld"; // toUpdate
        String emailToRemove = "remove@domain.tld"; // toDelete
        String emailToAdd = "add@domain.tld"; // toAdd
        String originalName = "Old Name";
        String updatedName = "New Name";

        // Initial vCard with two emails (keep, remove)
        String originalVcard = """
            BEGIN:VCARD
            VERSION:4.0
            FN:%s
            UID:%s
            EMAIL;TYPE=work:%s
            EMAIL;TYPE=home:%s
            END:VCARD
            """.formatted(originalName, uid, emailToKeep, emailToRemove);

        davClient.putCollectedContact(openPaasUser.email(), openPaasUser.id(), uid,
            originalVcard.getBytes(StandardCharsets.UTF_8)).block();

        awaitAtMost.untilAsserted(() -> {
            List<ContactFields> contactFieldsList = Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(),
                    originalName.toLowerCase(), 255)).map(EmailAddressContact::fields)
                .collectList().block();
            assertThat(contactFieldsList)
                .containsExactly(ContactFields.of(Throwing.supplier(() -> new MailAddress(emailToKeep)).get(), originalName),
                    ContactFields.of(Throwing.supplier(() -> new MailAddress(emailToRemove)).get(), originalName));
        });


        // Updated vCard:
        // - remove emailToRemove
        // - keep emailToKeep, but update name
        // - add emailToAdd
        String updatedVcard = """
            BEGIN:VCARD
            VERSION:4.0
            FN:%s
            UID:%s
            EMAIL;TYPE=work:%s
            EMAIL;TYPE=other:%s
            END:VCARD
            """.formatted(updatedName, uid, emailToKeep, emailToAdd);

        // When
        davClient.putCollectedContact(openPaasUser.email(), openPaasUser.id(), uid, updatedVcard.getBytes(StandardCharsets.UTF_8)).block();

        // Then
        // emailToRemove should be gone
        awaitAtMost.untilAsserted(() -> assertThat(
            Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(), "remove", 255))
                .map(EmailAddressContact::fields)
                .collectList().block()).isEmpty());

        // emailToKeep should be updated with new name
        awaitAtMost.untilAsserted(() -> assertThat(
            Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(), "keep", 255))
                .map(EmailAddressContact::fields)
                .collectList().block())
            .containsExactly(ContactFields.of(new MailAddress(emailToKeep), updatedName)));

        // emailToAdd should be indexed with new name
        awaitAtMost.untilAsserted(() -> assertThat(
            Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(), "add", 255))
                .map(EmailAddressContact::fields)
                .collectList().block())
            .containsExactly(ContactFields.of(new MailAddress(emailToAdd), updatedName)));
    }

    @Test
    void shouldPreserveOtherContactWhenDeletingOneOfMultipleWithSameEmail() throws Exception {
        // Given: two contacts with the same email address but different UIDs and full names are indexed
        String vcardUid1 = UUID.randomUUID().toString();
        String vcardUid2 = UUID.randomUUID().toString();

        String emailAddress = "vttran@exmaple.ltd";

        String vcard1 = """
            BEGIN:VCARD
            VERSION:4.0
            FN:Tung Tran
            UID:%s
            EMAIL;TYPE=work:%s
            END:VCARD""".formatted(vcardUid1, emailAddress);

        String vcard2 = """
            BEGIN:VCARD
            VERSION:4.0
            FN:Java Member
            UID:%s
            EMAIL;TYPE=work:%s
            END:VCARD""".formatted(vcardUid2, emailAddress);

        davClient.putCollectedContact(openPaasUser.email(), openPaasUser.id(), vcardUid1, vcard1.getBytes(StandardCharsets.UTF_8)).block();
        davClient.putCollectedContact(openPaasUser.email(), openPaasUser.id(), vcardUid2, vcard2.getBytes(StandardCharsets.UTF_8)).block();
        awaitContactIndexed(emailAddress, "Tung Tran");
        awaitContactIndexed(emailAddress, "Java Member");

        // When: one of the two contacts is deleted (vcardUid1)
        deleteCollectedContact(vcardUid1);
        Thread.sleep(200);

        // Then: the other contact with the same email but different UID should still exist in the index
        awaitAtMost.untilAsserted(() -> assertThat(Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(),
                emailAddress, 255)).map(EmailAddressContact::fields)
            .collectList().block()).containsExactly(ContactFields.of(new MailAddress(emailAddress), "Java Member")));
    }

    private CardDavCreationObjectRequest createContact(String mailAddress, String fullName) {
        MailAddress mailAddress1 = Throwing.supplier(() -> new MailAddress(mailAddress)).get();
        CardDavCreationObjectRequest cardDavCreationObjectRequest = CardDavUtils.createObjectCreationRequest(Optional.of(fullName),
            mailAddress1);

        davClient.createCollectedContact(openPaasUser.email(), openPaasUser.id(), cardDavCreationObjectRequest).block();
        awaitContactIndexed(mailAddress, fullName);
        return cardDavCreationObjectRequest;
    }

    private void awaitContactIndexed(String mailAddress, String fullName) {
        MailAddress mailAddress1 = Throwing.supplier(() -> new MailAddress(mailAddress)).get();
        awaitAtMost.untilAsserted(() -> {
            List<ContactFields> contactFieldsList = Flux.from(emailAddressContactSearchEngine.autoComplete(getAccountId(),
                    fullName.toLowerCase(), 255)).map(EmailAddressContact::fields)
                .collectList().block();
            assertThat(contactFieldsList)
                .containsExactly(ContactFields.of(mailAddress1, fullName));
        });
    }

    private AccountId getAccountId() {
        return AccountId.fromUsername(Username.of(openPaasUser.email()));
    }

    private void deleteCollectedContact(String vcardUid) {
        deleteCollectedContact(openPaasUser.email(), openPaasUser.id(), vcardUid);
    }

    private void deleteCollectedContact(String username, String userId, String vcardUid) {
        UsernamePasswordCredentials adminCredential = dockerOpenPaasExtension.dockerOpenPaasSetup().davConfiguration().adminCredential();
        davHTTPClient.headers(headers -> {
                headers.add(HttpHeaderNames.ACCEPT, "application/vcard+json");
                headers.add(HttpHeaderNames.AUTHORIZATION, HttpUtils.createBasicAuthenticationToken(new UsernamePasswordCredentials(
                    adminCredential.getUserName() + "&" + username, adminCredential.getPassword())));
            })
            .delete()
            .uri(String.format("/addressbooks/%s/collected/%s.vcf", userId, vcardUid))
            .responseSingle((response, byteBufMono) ->
                switch (response.status().code()) {
                    case 204 -> Mono.empty();
                    default -> Mono.error(new DavClientException(
                        String.format("Unexpected status code: %d when deleted contact for user: %s and collected id: %s", response.status().code(), userId, vcardUid)));
                }).block();
    }
}
