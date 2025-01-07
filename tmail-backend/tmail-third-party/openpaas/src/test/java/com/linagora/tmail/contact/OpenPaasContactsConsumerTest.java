package com.linagora.tmail.contact;

import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED;
import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_QUEUES_QUORUM_BYPASS_ENABLED;
import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.RabbitMQExtension.IsolationPolicy.STRONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;

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
import com.google.common.collect.ImmutableList;
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
        .isolationPolicy(STRONG);

    private EmailAddressContactSearchEngine searchEngine;
    private OpenPaasContactsConsumer consumer;

    @BeforeEach
    void setup() throws URISyntaxException {
        OpenPaasConfiguration openPaasConfiguration = new OpenPaasConfiguration(
            openPaasServerExtension.getBaseUrl(),
            OpenPaasServerExtension.GOOD_USER(),
            OpenPaasServerExtension.GOOD_PASSWORD(),
            OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED,
            new OpenPaasConfiguration.ContactConsumerConfiguration(
                ImmutableList.of(AmqpUri.from(rabbitMQExtension.getRabbitMQ().amqpUri())),
            OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED));
        OpenPaasRestClient restClient = new OpenPaasRestClient(openPaasConfiguration);
        searchEngine = new InMemoryEmailAddressContactSearchEngine();
        consumer = new OpenPaasContactsConsumer(rabbitMQExtension.getRabbitChannelPool(),
            openPaasConfiguration.contactConsumerConfiguration().get().amqpUri().getFirst().toRabbitMqConfiguration(rabbitMQExtension.getRabbitMQ().withQuorumQueueConfiguration()).build(),
            searchEngine, restClient, openPaasConfiguration);
    }

    @AfterEach
    void afterEach() {
        consumer.close();
    }

    @Test
    void openPaasContactsQueueShouldBeQuorumQueueWhenQuorumQueuesAreEnabled() throws Exception {
        consumer.start();

        assertThat(rabbitMQExtension.managementAPI()
            .queueDetails("/", "openpaas-contacts-queue-add")
            .getArguments())
            .containsEntry("x-queue-type", "quorum");
    }

    @Test
    void openPaasContactsQueueShouldBeClassicQueueWhenQuorumQueuesBypassEnabled() throws Exception {
        OpenPaasConfiguration openPaasConfiguration = new OpenPaasConfiguration(
            openPaasServerExtension.getBaseUrl(),
            OpenPaasServerExtension.GOOD_USER(),
            OpenPaasServerExtension.GOOD_PASSWORD(),
            OPENPAAS_REST_CLIENT_TRUST_ALL_SSL_CERTS_DISABLED,
            new OpenPaasConfiguration.ContactConsumerConfiguration(
                ImmutableList.of(AmqpUri.from(rabbitMQExtension.getRabbitMQ().amqpUri())),
                OPENPAAS_QUEUES_QUORUM_BYPASS_ENABLED));
        OpenPaasRestClient restClient = new OpenPaasRestClient(openPaasConfiguration);
        searchEngine = new InMemoryEmailAddressContactSearchEngine();
        consumer = new OpenPaasContactsConsumer(rabbitMQExtension.getRabbitChannelPool(),
            openPaasConfiguration.contactConsumerConfiguration().get().amqpUri().getFirst().toRabbitMqConfiguration(rabbitMQExtension.getRabbitMQ().withQuorumQueueConfiguration()).build(),
            searchEngine, restClient, openPaasConfiguration);
        consumer.start();

        assertThat(rabbitMQExtension.managementAPI()
            .queueDetails("/", "openpaas-contacts-queue-add")
            .getArguments())
            .doesNotContainEntry("x-queue-type", "quorum")
            .containsEntry("x-queue-version", "2");
    }

    @Test
    void consumeMessageShouldNotCrashOnInvalidMessages() throws InterruptedException {
        consumer.start();

        IntStream.range(0, 10).forEach(i -> sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, "BAD_PAYLOAD" + i));

        TimeUnit.MILLISECONDS.sleep(100);

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
            {
                 "_id": "ALICE_USER_ID",
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
        consumer.start();

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
            {
                 "_id": "ALICE_USER_ID",
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
    void multipleAddressesShouldBeIndexedWhenContactUserAddedMessage() {
        consumer.start();

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
            {
                 "_id": "ALICE_USER_ID",
                "vcard": [
                "vcard",
                  [
                     [ "version", {}, "text", "4.0" ],
                     [ "kind",    {}, "text", "individual" ],
                     [ "fn",      {}, "text", "Jane Doe" ],
                     [ "email",   {}, "text", "john@doe.com" ],
                     [ "email",   {}, "text", "another.john@doe.com" ],
                     [ "org",     {}, "text", [ "ABC, Inc.", "North American Division", "Marketing" ] ]
                  ]
               ]
            }
            """);

        await().timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
                .collectList().block())
                .map(EmailAddressContact::fields)
                .containsExactlyInAnyOrder(
                    new ContactFields(new MailAddress("john@doe.com"), "Jane Doe", ""),
                    new ContactFields(new MailAddress("another.john@doe.com"), "Jane Doe", "")));
    }

    @Test
    void contactShouldBeUpdatedWhenContactUserUpdatedMessageWithSameAddress() {
        consumer.start();

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
            {
                "_id": "ALICE_USER_ID",
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
                "_id": "ALICE_USER_ID",
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
        consumer.start();

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
            {
                "_id": "ALICE_USER_ID",
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
                "_id": "ALICE_USER_ID",
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
        consumer.start();

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_DELETE,"""
            {
                "_id": "ALICE_USER_ID",
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
        consumer.start();

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
            {
                "_id": "ALICE_USER_ID",
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
                "_id": "ALICE_USER_ID",
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
        consumer.start();

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
            {
                "_id": "ALICE_USER_ID",
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
        consumer.start();

        // Note: Bob has an invalid mail address.
        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
            {
                "_id": "BOB_USER_ID",
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
                "_id": "ALICE_USER_ID",
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
        consumer.start();

        indexJohnDoe(OpenPaasServerExtension.ALICE_EMAIL());

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, """
            {
                "_id": "ALICE_USER_ID",
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
        consumer.start();

        ContactFields indexedContact = indexJohnDoe(OpenPaasServerExtension.ALICE_EMAIL());

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, """
            {
                "_id": "ALICE_USER_ID",
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
        consumer.start();

        IntStream.range(0, 10).forEach(i -> sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, "BAD_PAYLOAD" + i));

        TimeUnit.MILLISECONDS.sleep(100);

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, """
            {
                "unknownProperty": "value",
                "_id": "ALICE_USER_ID",
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
        consumer.start();

        IntStream.range(0, 10).forEach(i -> sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, "BAD_PAYLOAD" + i));

        TimeUnit.MILLISECONDS.sleep(100);

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, """
            {
                "_id": "ALICE_USER_ID",
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
        consumer.start();

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, """
            {   "unknownProperty": "value",
                "_id": "ALICE_USER_ID",
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

    @Test
    void contactShouldBeIndexedWhenFallbackOpenPaasIdToUserId() {
        consumer.start();

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, """
            {
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

    @Test
    void contactShouldBeIndexedWhenFallbackToIdWithinUserBlock() {
        consumer.start();

        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD, """
        {
            "vcard": [
                "vcard",
                [
                    [
                        "unknownProperty",
                        {},
                        "text",
                        "4.0"
                    ],
                    [
                        "version",
                        {},
                        "text",
                        "4.0"
                    ],
                    [
                        "kind",
                        {},
                        "text",
                        "individual"
                    ],
                    [
                        "fn",
                        {},
                        "text",
                        "Jane Doe"
                    ],
                    [
                        "email",
                        {},
                        "text",
                        "jhon@doe.com"
                    ],
                    [
                        "org",
                        {},
                        "text",
                        [
                            "ABC, Inc.",
                            "North American Division",
                            "Marketing"
                        ]
                    ]
                ]
            ],
            "user": {
                "timestamps": {
                    "creation": "2024-12-06T22:39:53.619Z"
                },
                "login": {
                    "failures": []
                },
                "schemaVersion": 2,
                "avatars": [],
                "_id": "ALICE_USER_ID",
                "domains": [
                    {
                        "joined_at": "2024-12-06T22:39:53.619Z",
                        "domain_id": "66d6d18bbfa4450079d8eb54"
                    }
                ],
                "accounts": [
                    {
                        "timestamps": {
                            "creation": "2024-12-06T22:39:53.619Z"
                        },
                        "hosted": true,
                        "emails": [
                            "adoe@linagora.com"
                        ],
                        "preferredEmailIndex": 0,
                        "type": "email"
                    }
                ],
                "states": [],
                "__v": 0
            }
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
    void consumeMessageShouldNotCrashWhenAbsentOpenPassId() throws InterruptedException {
        consumer.start();

        // Note: _id is absent in the message.
        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
            {
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

        Thread.sleep(500);

        // Note: id is present in the message.
        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
            {
                "_id": "ALICE_USER_ID",
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
    void contactShouldNotIndexedWhenAddedMessageAbsentOpenPassId() throws InterruptedException {
        sendMessage(OpenPaasContactsConsumer.EXCHANGE_NAME_ADD,"""
            {
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

       Thread.sleep(500);
        assertThat(Flux.from(searchEngine.autoComplete(AccountId.fromString(OpenPaasServerExtension.ALICE_EMAIL()), "john", 10))
            .collectList().block())
            .hasSize(0);
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