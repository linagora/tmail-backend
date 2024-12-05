package com.linagora.tmail.contact;

import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.RabbitMQExtension.IsolationPolicy.WEAK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import jakarta.mail.internet.AddressException;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.AmqpUri;
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.api.OpenPaasServerExtension;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngine;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;

class OpenPaasContactsConsumerTest {

    @RegisterExtension
    static OpenPaasServerExtension openPaasServerExtension = new OpenPaasServerExtension();

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(WEAK);

    private EmailAddressContactSearchEngine searchEngine;
    private OpenPaasContactsConsumer consumer;

    @BeforeEach
    void setup() throws URISyntaxException {
        OpenPaasRestClient restClient = new OpenPaasRestClient(
            new OpenPaasConfiguration(
                AmqpUri.from(rabbitMQExtension.getRabbitMQ().amqpUri()),
                openPaasServerExtension.getBaseUrl().toURI(),
                OpenPaasServerExtension.GOOD_USER(),
                OpenPaasServerExtension.GOOD_PASSWORD(),
                OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED));
        searchEngine = new InMemoryEmailAddressContactSearchEngine();
        consumer = new OpenPaasContactsConsumer(rabbitMQExtension.getRabbitChannelPool(),
            rabbitMQExtension.getRabbitMQ().withQuorumQueueConfiguration(),
            searchEngine, restClient);

        consumer.start();
    }

    @AfterEach
    void afterEach() throws IOException {
        consumer.close();
    }

    @Test
    void consumeMessageShouldNotCrashOnInvalidMessages() throws InterruptedException {
        IntStream.range(0, 10).forEach(i -> sendMessage("BAD_PAYLOAD" + i));

        TimeUnit.MILLISECONDS.sleep(100);

        sendMessage("""
            {
                "bookId": "ALICE_USER_ID",
                "bookName": "contacts",
                "contactId": "fd9b3c98-fc77-4187-92ac-d9f58d400968",
                "userId": "ALICE_USER_ID",
                "vcard": [
                "vcard",
                  [
                     [ "version", {}, "text", "4.0" ],
                     [ "kind",    {}, "text", "individual" ],
                     [ "fn",      {}, "text", "Jane Doe" ],
                     [ "email",   {}, "text", "jhon@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "jhon", 10))
                    .collectList().block())
                .hasSize(1));
    }

    @Test
    void contactShouldBeIndexedWhenContactUserAddedMessage() {
        sendMessage("""
            {
                "bookId": "ALICE_USER_ID",
                "bookName": "contacts",
                "contactId": "fd9b3c98-fc77-4187-92ac-d9f58d400968",
                "userId": "ALICE_USER_ID",
                "vcard": [
                "vcard",
                  [
                     [ "version", {}, "text", "4.0" ],
                     [ "kind",    {}, "text", "individual" ],
                     [ "fn",      {}, "text", "Jane Doe" ],
                     [ "email",   {}, "text", "jhon@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "jhon", 10))
                    .collectList().block())
                .hasSize(1)
                .map(EmailAddressContact::fields)
                .allSatisfy(Throwing.consumer(contact -> {
                    assertThat(contact.address()).isEqualTo(new MailAddress("jhon@doe.com"));
                    assertThat(contact.firstname()).isEqualTo("Jane Doe");
                    assertThat(contact.surname()).isEqualTo("");
                })));
    }

    @Test
    void consumeMessageShouldNotCrashOnInvalidContactMailAddress() {
        sendMessage("""
            {
                "bookId": "ALICE_USER_ID",
                "bookName": "contacts",
                "contactId": "fd9b3c98-fc77-4187-92ac-d9f58d400968",
                "userId": "ALICE_USER_ID",
                "vcard": [
                "vcard",
                  [
                     [ "version", {}, "text", "4.0" ],
                     [ "kind",    {}, "text", "individual" ],
                     [ "fn",      {}, "text", "Jane Doe" ],
                     [ "email",   {}, "text", "BAD_MAIL_ADDRESS" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "jhon", 10))
                    .collectList().block()).isEmpty());

        sendMessage("""
            {
                "bookId": "ALICE_USER_ID",
                "bookName": "contacts",
                "contactId": "fd9b3c98-fc77-4187-92ac-d9f58d400968",
                "userId": "ALICE_USER_ID",
                "vcard": [
                "vcard",
                  [
                     [ "version", {}, "text", "4.0" ],
                     [ "kind",    {}, "text", "individual" ],
                     [ "fn",      {}, "text", "Jane Doe" ],
                     [ "email",   {}, "text", "jhon@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "jhon", 10))
                    .collectList().block())
                .hasSize(1));
    }

    @Test
    void consumeMessageShouldNotCrashWhenFnPropertyIsNotProvided() {
        sendMessage("""
            {
                "bookId": "ALICE_USER_ID",
                "bookName": "contacts",
                "contactId": "fd9b3c98-fc77-4187-92ac-d9f58d400968",
                "userId": "ALICE_USER_ID",
                "vcard": [
                "vcard",
                  [
                     [ "version", {}, "text", "4.0" ],
                     [ "kind",    {}, "text", "individual" ],
                     [ "email",   {}, "text", "jhon@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "jhon", 10))
                    .collectList().block())
                .hasSize(1)
                .map(EmailAddressContact::fields)
                .allSatisfy(Throwing.consumer(contact -> {
                    assertThat(contact.firstname()).isEmpty();
                    assertThat(contact.surname()).isEmpty();
                    assertThat(contact.address()).isEqualTo(new MailAddress("jhon@doe.com"));
                })));
    }

    @Test
    void consumeMessageShouldNotCrashOnInvalidOwnerMailAddress() {
        // Note: Bob has an invalid mail address.
        sendMessage("""
            {
                "bookId": "BOB_USER_ID",
                "bookName": "contacts",
                "contactId": "fd9b3c98-fc77-4187-92ac-d9f58d400968",
                "userId": "BOB_USER_ID",
                "vcard": [
                "vcard",
                  [
                     [ "version", {}, "text", "4.0" ],
                     [ "kind",    {}, "text", "individual" ],
                     [ "fn",      {}, "text", "Jane Doe" ],
                     [ "email",   {}, "text", "jhon@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "jhon", 10))
                    .collectList().block())
                .isEmpty());

        sendMessage("""
            {
                "bookId": "ALICE_USER_ID",
                "bookName": "contacts",
                "contactId": "fd9b3c98-fc77-4187-92ac-d9f58d400968",
                "userId": "ALICE_USER_ID",
                "vcard": [
                "vcard",
                  [
                     [ "version", {}, "text", "4.0" ],
                     [ "kind",    {}, "text", "individual" ],
                     [ "fn",      {}, "text", "Jane Doe" ],
                     [ "email",   {}, "text", "jhon@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "jhon", 10))
                    .collectList().block())
                .hasSize(1));
    }

    @Test
    void givenDisplayNameFromOpenPaasNotEmptyThenStoredDisplayNameShouldBeOverridden()
        throws AddressException {
        indexJhonDoe(OpenPaasServerExtension.ALICE_EMAIL());

        sendMessage("""
            {
                "bookId": "ALICE_USER_ID",
                "bookName": "contacts",
                "contactId": "fd9b3c98-fc77-4187-92ac-d9f58d400968",
                "userId": "ALICE_USER_ID",
                "vcard": [
                "vcard",
                  [
                     [ "version", {}, "text", "4.0" ],
                     [ "kind",    {}, "text", "individual" ],
                     [ "fn",      {}, "text", "Jhon Dont" ],
                     [ "email",   {}, "text", "jhon@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        ContactFields expectedContact =
            new ContactFields(new MailAddress("jhon@doe.com"), "Jhon Dont", "");

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "jhon", 10))
                    .collectList().block())
                .hasSize(1)
                .map(EmailAddressContact::fields)
                .first()
                .isEqualTo(expectedContact));
    }

    @Test
    void givenDisplayNameFromOpenPaasIsEmptyThenStoredDisplayNameShouldPersist() {
        ContactFields indexedContact = indexJhonDoe(OpenPaasServerExtension.ALICE_EMAIL());

        sendMessage("""
            {
                "bookId": "ALICE_USER_ID",
                "bookName": "contacts",
                "contactId": "fd9b3c98-fc77-4187-92ac-d9f58d400968",
                "userId": "ALICE_USER_ID",
                "vcard": [
                "vcard",
                  [
                     [ "version", {}, "text", "4.0" ],
                     [ "kind",    {}, "text", "individual" ],
                     [ "fn",      {}, "text", "  " ],
                     [ "email",   {}, "text", "jhon@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "jhon", 10))
                    .collectList().block())
                .hasSize(1)
                .map(EmailAddressContact::fields)
                .first()
                .isEqualTo(indexedContact));
    }


    private void sendMessage(String message) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(
                OpenPaasContactsConsumer.EXCHANGE_NAME,
                EMPTY_ROUTING_KEY,
                message.getBytes(UTF_8))))
            .block();
    }

    private ContactFields indexJhonDoe(String ownerMailAddressString) {
        try {
            MailAddress ownerMailAddress = new MailAddress(ownerMailAddressString);
            MailAddress jhonDoeMailAddress = new MailAddress("jhon@doe.com");
            AccountId aliceAccountId =
                AccountId.fromUsername(Username.fromMailAddress(ownerMailAddress));

            return Mono.from(searchEngine.index(aliceAccountId,
                new ContactFields(jhonDoeMailAddress, "Jhon", "Doe"))).block().fields();
        }
        catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }
}