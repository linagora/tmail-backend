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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.AmqpUri;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.api.OpenPaasServerExtension;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngine;

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
        IntStream.range(0, 10).forEach(i -> sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, "BAD_PAYLOAD" + i));

        TimeUnit.MILLISECONDS.sleep(100);

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
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
                     [ "email",   {}, "text", "john@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                    .collectList().block())
                .hasSize(1));
    }

    @Test
    void contactShouldBeIndexedWhenContactUserAddedMessage() {
        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
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
                     [ "email",   {}, "text", "john@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                    .collectList().block())
                .hasSize(1)
                .map(EmailAddressContact::fields)
                .allSatisfy(Throwing.consumer(contact -> {
                    assertThat(contact.address()).isEqualTo(new MailAddress("john@doe.com"));
                    assertThat(contact.firstname()).isEqualTo("Jane Doe");
                    assertThat(contact.surname()).isEqualTo("");
                })));
    }

    @Test
    void contactShouldBeUpdatedWhenContactUserUpdatedMessageWithSameAddress() {

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
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
                     [ "email",   {}, "text", "john@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(
                        Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                                .collectList().block())
                        .hasSize(1)
                        .map(EmailAddressContact::fields)
                        .allSatisfy(Throwing.consumer(contact -> {
                            assertThat(contact.address()).isEqualTo(new MailAddress("john@doe.com"));
                            assertThat(contact.firstname()).isEqualTo("Jane Doe");
                            assertThat(contact.surname()).isEqualTo("");
                        })));

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_UPDATE,"""
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
                     [ "fn",      {}, "text", "Updated Jane Doe" ],
                     [ "email",   {}, "text", "john@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(
                        Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                                .collectList().block())
                        .hasSize(1)
                        .map(EmailAddressContact::fields)
                        .anySatisfy(Throwing.consumer(contact -> {
                            assertThat(contact.address()).isEqualTo(new MailAddress("john@doe.com"));
                            assertThat(contact.firstname()).isEqualTo("Updated Jane Doe");
                            assertThat(contact.surname()).isEqualTo("");
                        })));
    }

    @Test
    void newContactShouldBeAddedWhenContactUserUpdatedMessageWithUpdatedAddress() {

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
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
                     [ "email",   {}, "text", "john@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(
                        Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                                .collectList().block())
                        .hasSize(1)
                        .map(EmailAddressContact::fields)
                        .allSatisfy(Throwing.consumer(contact -> {
                            assertThat(contact.address()).isEqualTo(new MailAddress("john@doe.com"));
                            assertThat(contact.firstname()).isEqualTo("Jane Doe");
                            assertThat(contact.surname()).isEqualTo("");
                        })));

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_UPDATE,"""
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
                     [ "fn",      {}, "text", "Updated Jane Doe" ],
                     [ "email",   {}, "text", "john2@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(
                        Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                                .collectList().block())
                        .hasSize(2)
                        .map(EmailAddressContact::fields)
                        .anySatisfy(Throwing.consumer(contact -> {
                            assertThat(contact.address()).isEqualTo(new MailAddress("john2@doe.com"));
                            assertThat(contact.firstname()).isEqualTo("Updated Jane Doe");
                            assertThat(contact.surname()).isEqualTo("");
                        })));
    }

    @Test
    void contactShouldBeDeletedWhenContactUserDeletedMessage() {
        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_DELETE,"""
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
                     [ "email",   {}, "text", "john@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(
                        Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                                .collectList().block())
                        .hasSize(0));
    }

    @Test
    void consumeMessageShouldNotCrashOnInvalidContactMailAddress() {
        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
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
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                    .collectList().block()).isEmpty());

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
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
                     [ "email",   {}, "text", "john@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                    .collectList().block())
                .hasSize(1));
    }

    @Test
    void consumeMessageShouldNotCrashWhenFnPropertyIsNotProvided() {
        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
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
                     [ "email",   {}, "text", "john@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                    .collectList().block())
                .hasSize(1)
                .map(EmailAddressContact::fields)
                .allSatisfy(Throwing.consumer(contact -> {
                    assertThat(contact.firstname()).isEmpty();
                    assertThat(contact.surname()).isEmpty();
                    assertThat(contact.address()).isEqualTo(new MailAddress("john@doe.com"));
                })));
    }

    @Test
    void consumeMessageShouldNotCrashOnInvalidOwnerMailAddress() {
        // Note: Bob has an invalid mail address.
        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
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
                     [ "email",   {}, "text", "john@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                    .collectList().block())
                .isEmpty());

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
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
                     [ "email",   {}, "text", "john@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                    .collectList().block())
                .hasSize(1));
    }

    @Test
    void givenDisplayNameFromOpenPaasNotEmptyThenStoredDisplayNameShouldBeOverridden()
        throws AddressException {
        indexJohnDoe(OpenPaasServerExtension.ALICE_EMAIL());

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, """
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
                     [ "fn",      {}, "text", "John Dont" ],
                     [ "email",   {}, "text", "john@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        ContactFields expectedContact =
            new ContactFields(new MailAddress("john@doe.com"), "John Dont", "");

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                    .collectList().block())
                .hasSize(1)
                .map(EmailAddressContact::fields)
                .first()
                .isEqualTo(expectedContact));
    }

    @Test
    void givenDisplayNameFromOpenPaasIsEmptyThenStoredDisplayNameShouldPersist() {
        ContactFields indexedContact = indexJohnDoe(OpenPaasServerExtension.ALICE_EMAIL());

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, """
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
                     [ "email",   {}, "text", "john@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(
                Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                    .collectList().block())
                .hasSize(1)
                .map(EmailAddressContact::fields)
                .first()
                .isEqualTo(indexedContact));
    }

    @Test
    void consumeMessageShouldNotCrashOnUnknownProperty() throws InterruptedException {
        IntStream.range(0, 10).forEach(i -> sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, "BAD_PAYLOAD" + i));

        TimeUnit.MILLISECONDS.sleep(100);

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, """
            {
                "unknownProperty": "value",
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
    void consumeMessageShouldNotCrashOnUnknownPropertyOfVCardObject() throws InterruptedException {
        IntStream.range(0, 10).forEach(i -> sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, "BAD_PAYLOAD" + i));

        TimeUnit.MILLISECONDS.sleep(100);

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, """
            {
                "bookId": "ALICE_USER_ID",
                "bookName": "contacts",
                "contactId": "fd9b3c98-fc77-4187-92ac-d9f58d400968",
                "userId": "ALICE_USER_ID",
                "vcard": [
                "vcard",
                  [
                     [ "unknownProperty", {}, "text", "4.0" ],
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
    void contactShouldBeIndexedWhenMessageHasUnknownProperty() {
        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, """
            {   "unknownProperty": "value",
                "bookId": "ALICE_USER_ID",
                "bookName": "contacts",
                "contactId": "fd9b3c98-fc77-4187-92ac-d9f58d400968",
                "userId": "ALICE_USER_ID",
                "vcard": [
                "vcard",
                  [
                     [ "unknownProperty", {}, "text", "4.0" ],
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


    private void sendMessage(String exchange, String message) {
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(
                exchange,
                EMPTY_ROUTING_KEY,
                message.getBytes(UTF_8))))
            .block();
    }

    private ContactFields indexJohnDoe(String ownerMailAddressString) {
        try {
            MailAddress ownerMailAddress = new MailAddress(ownerMailAddressString);
            MailAddress johnDoeMailAddress = new MailAddress("john@doe.com");
            AccountId aliceAccountId =
                AccountId.fromUsername(Username.fromMailAddress(ownerMailAddress));

            return Mono.from(searchEngine.index(aliceAccountId,
                new ContactFields(johnDoeMailAddress, "John", "Doe"))).block().fields();
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }
}