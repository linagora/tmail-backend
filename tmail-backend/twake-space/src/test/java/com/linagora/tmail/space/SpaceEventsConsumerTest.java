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

package com.linagora.tmail.space;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.backends.rabbitmq.RabbitMQExtension.IsolationPolicy.WEAK;
import static org.apache.james.events.EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.InMemoryDNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.rabbitmq.ManagedRabbitMQConsumer;
import com.linagora.tmail.space.SpaceAnnotations.SpaceBinding;
import com.linagora.tmail.space.SpaceMailboxes.SpaceMailbox;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxMember;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;
import com.rabbitmq.client.BuiltinExchangeType;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.QueueSpecification;
import scala.jdk.javaapi.OptionConverters;

class SpaceEventsConsumerTest {
    record Published(String exchange, String routingKey, String messageId, String body) {
    }

    private static final Domain DOMAIN = Domain.of("acme.com");
    private static final String ORGANIZATION = "org-1";
    private static final String SPACE = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_SPACE = "22222222-2222-2222-2222-222222222222";
    private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");
    private static final Username ALICE = Username.of("alice@acme.com");
    private static final Username BOB = Username.of("bob@acme.com");
    private static final Username CAROL = Username.of("carol@acme.com");
    private static final String DNS_EXCHANGE = "admin-panel";

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(WEAK);

    private InMemoryMailboxManager mailboxManager;
    private MessageIdManager messageIdManager;
    private TeamMailboxRepositoryImpl teamMailboxRepository;
    private SpaceMailboxes spaceMailboxes;
    private SpacePublisher publisher;
    private SpaceReconciler reconciler;
    private SpaceEventsConsumer testee;
    private SpaceConfiguration configuration;
    private UpdatableTickingClock clock;
    private Set<MailAddress> mailingLists;
    private ConcurrentLinkedQueue<Published> published;
    private Disposable consumer;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .eventBus(new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), RETRY_BACKOFF_CONFIGURATION, new MemoryEventDeadLetters()))
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();
        mailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();
        teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager,
            new StoreSubscriptionManager(mailboxManager.getMapperFactory(), mailboxManager.getMapperFactory(), mailboxManager.getEventBus()),
            mailboxManager.getMapperFactory(),
            Set.of());
        MemoryDomainList domainList = domainListWith(DOMAIN);

        String suffix = UUID.randomUUID().toString();
        configuration = new SpaceConfiguration(Optional.empty(), "tmail-space-" + suffix, "tmail-space-dead-letter-" + suffix,
            "space-" + suffix, DNS_EXCHANGE, "dns.validated", "activity-" + suffix, "", Duration.ofDays(30), Duration.ofHours(1));
        clock = new UpdatableTickingClock(NOW);
        mailingLists = new HashSet<>();
        spaceMailboxes = new SpaceMailboxes(mailboxManager, mailboxManager.getMapperFactory(), teamMailboxRepository);
        publisher = SpacePublisher.create(configuration, rabbitMQExtension.getRabbitMQ().getConfiguration(), clock);
        reconciler = new SpaceReconciler(spaceMailboxes, teamMailboxRepository, domainList, address -> mailingLists.contains(address),
            publisher, configuration, clock);
        testee = new SpaceEventsConsumer(reconciler, publisher, configuration);

        String queue = "space-test-" + suffix;
        rabbitMQExtension.getSender().declareExchange(topicExchange(configuration.activityExchange())).block();
        rabbitMQExtension.getSender().declareExchange(topicExchange(configuration.spaceExchange())).block();
        rabbitMQExtension.getSender().declareQueue(QueueSpecification.queue(queue).autoDelete(true)).block();
        rabbitMQExtension.getSender().bind(BindingSpecification.binding(configuration.activityExchange(), "#", queue)).block();
        rabbitMQExtension.getSender().bind(BindingSpecification.binding(configuration.spaceExchange(), SpacePublisher.SYNC_REQUESTED, queue)).block();
        published = new ConcurrentLinkedQueue<>();
        consumer = rabbitMQExtension.getReceiverProvider().createReceiver()
            .consumeAutoAck(queue)
            .subscribe(delivery -> published.add(new Published(
                delivery.getEnvelope().getExchange(),
                delivery.getEnvelope().getRoutingKey(),
                delivery.getProperties().getMessageId(),
                new String(delivery.getBody(), UTF_8))));
    }

    @AfterEach
    void tearDown() {
        consumer.dispose();
        publisher.close();
    }

    @Test
    void createdShouldProvisionTheTeamMailbox() {
        handle("twake.space.created", created(SPACE, "Équipe Ventes", member(ALICE, "admin"), member(BOB, "editor"), member(CAROL, "viewer")));

        TeamMailbox teamMailbox = teamMailbox("equipe-ventes");
        assertThat(members(teamMailbox))
            .containsExactlyInAnyOrder(TeamMailboxMember.asManager(ALICE), TeamMailboxMember.asMember(BOB));
        assertThat(spaceMailbox(teamMailbox).binding())
            .contains(new SpaceBinding(ORGANIZATION, SPACE, Optional.empty()));
    }

    @Test
    void createdShouldPublishProvisioned() {
        handle("twake.space.created", created(SPACE, "Sales", member(ALICE, "admin")));

        String rootId = spaceMailbox(teamMailbox("sales")).rootId().serialize();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published).hasSize(1));
        Published provisioned = published.peek();
        assertThat(provisioned.routingKey()).isEqualTo(SpacePublisher.PROVISIONED);
        assertThat(provisioned.messageId()).isEqualTo(SPACE + ":" + rootId + ":provisioned");
        assertThatJson(provisioned.body()).isEqualTo("""
            {
              "specversion": "1.0",
              "id": "%s:%s:provisioned",
              "source": "twake://mail",
              "type": "com.twake.mail.space.provisioned.v1",
              "time": "2026-10-09T08:00:00Z",
              "twakeorg": "org-1",
              "data": {"space_id": "%s", "resource": {"kind": "mailbox", "id": "%s"}}
            }""".formatted(SPACE, rootId, SPACE, rootId));
    }

    @Test
    void createdTwiceShouldKeepASingleTeamMailbox() {
        handle("twake.space.created", created(SPACE, "Sales", member(ALICE, "admin")));
        handle("twake.space.created", created(SPACE, "Sales", member(ALICE, "admin")));

        assertThat(Flux.from(teamMailboxRepository.listTeamMailboxes(DOMAIN)).collectList().block())
            .containsExactly(teamMailbox("sales"));
    }

    @Test
    void createdShouldUseTheTeamMailboxPrefix() {
        configuration = new SpaceConfiguration(Optional.empty(), configuration.queue(), configuration.deadLetterQueue(),
            configuration.spaceExchange(), configuration.dnsExchange(), configuration.dnsRoutingKey(), configuration.activityExchange(),
            "space-", configuration.retention(), configuration.purgeInterval());
        reconciler = new SpaceReconciler(spaceMailboxes, teamMailboxRepository, domainListWith(DOMAIN), SpaceReconciler.MailingLists.NONE,
            publisher, configuration, clock);
        testee = new SpaceEventsConsumer(reconciler, publisher, configuration);

        handle("twake.space.created", created(SPACE, "Sales"));

        assertThat(spaceMailbox(teamMailbox("space-sales")).binding()).isPresent();
    }

    @Test
    void anotherSpaceWithTheSameNameShouldGetASuffix() {
        handle("twake.space.created", created(SPACE, "Sales"));
        handle("twake.space.created", created(OTHER_SPACE, "Sales"));

        assertThat(spaceMailbox(teamMailbox("sales-2")).binding().map(SpaceBinding::spaceId)).contains(OTHER_SPACE);
    }

    @Test
    void aMailingListAddressShouldBeSkipped() throws Exception {
        mailingLists.add(new MailAddress("sales@acme.com"));

        handle("twake.space.created", created(SPACE, "Sales"));

        assertThat(spaceMailboxes.read(teamMailbox("sales")).blockOptional()).isEmpty();
        assertThat(spaceMailbox(teamMailbox("sales-2")).binding().map(SpaceBinding::spaceId)).contains(SPACE);
    }

    @Test
    void aTeamMailboxNoSpaceHoldsShouldBeAdopted() {
        Mono.from(teamMailboxRepository.createTeamMailbox(teamMailbox("sales"))).block();
        Mono.from(teamMailboxRepository.addMember(teamMailbox("sales"), TeamMailboxMember.asMember(CAROL))).block();

        handle("twake.space.created", created(SPACE, "Sales", member(ALICE, "admin")));

        assertThat(spaceMailbox(teamMailbox("sales")).binding())
            .contains(new SpaceBinding(ORGANIZATION, SPACE, Optional.empty()));
        assertThat(members(teamMailbox("sales"))).containsExactly(TeamMailboxMember.asManager(ALICE));
        assertThat(Flux.from(teamMailboxRepository.listTeamMailboxes(DOMAIN)).collectList().block())
            .containsExactly(teamMailbox("sales"));
    }

    @Test
    void aCrashBeforeTheAnnotationsShouldBeRecoveredByTheNextEvent() throws Exception {
        TeamMailbox sales = teamMailbox("sales");
        MailboxSession session = mailboxManager.createSystemSession(sales.owner());
        mailboxManager.createMailbox(sales.mailboxPath(), session);

        handle("twake.space.created", created(SPACE, "Sales", member(ALICE, "admin")));

        assertThat(spaceMailbox(sales).isLiveFor(SPACE)).isTrue();
        assertThat(members(sales)).containsExactly(TeamMailboxMember.asManager(ALICE));
        assertThat(mailboxManager.mailboxExists(sales.inboxPath(), session).block()).isTrue();
    }

    @Test
    void anUnknownDomainShouldBeSkipped() {
        handle("twake.space.created", """
            {"organizationId": "org-1", "organizationDomain": "unknown.com", "id": "%s", "name": "Sales", "members": []}
            """.formatted(SPACE));

        assertThat(Flux.from(teamMailboxRepository.listTeamMailboxes()).collectList().block()).isEmpty();
    }

    @Test
    void syncedShouldMakeTheMembersMatch() {
        handle("twake.space.created", created(SPACE, "Sales", member(ALICE, "admin"), member(BOB, "editor")));
        Mono.from(teamMailboxRepository.addMember(teamMailbox("sales"), TeamMailboxMember.asMember(CAROL))).block();

        handle("twake.space.synced", created(SPACE, "Sales renamed", member(ALICE, "editor"), member(BOB, "viewer")));

        assertThat(members(teamMailbox("sales")))
            .containsExactly(TeamMailboxMember.asMember(ALICE));
    }

    @Test
    void syncedShouldRepublishProvisionedUnderTheSameId() {
        handle("twake.space.created", created(SPACE, "Sales"));
        handle("twake.space.synced", created(SPACE, "Sales"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published)
            .extracting(Published::messageId)
            .hasSize(2)
            .containsOnly(SPACE + ":" + spaceMailbox(teamMailbox("sales")).rootId().serialize() + ":provisioned"));
    }

    @Test
    void memberAddedShouldAddOnlyTheNamedMembers() {
        handle("twake.space.created", created(SPACE, "Sales", member(ALICE, "admin")));

        handle("twake.space.member.added", memberEvent(SPACE, member(BOB, "editor"), member(CAROL, "viewer")));

        assertThat(members(teamMailbox("sales")))
            .containsExactlyInAnyOrder(TeamMailboxMember.asManager(ALICE), TeamMailboxMember.asMember(BOB));
    }

    @Test
    void roleChangedShouldChangeTheRole() {
        handle("twake.space.created", created(SPACE, "Sales", member(ALICE, "admin"), member(BOB, "editor")));

        handle("twake.space.member.role.changed", memberEvent(SPACE, member(ALICE, "editor"), member(BOB, "viewer")));

        assertThat(members(teamMailbox("sales")))
            .containsExactly(TeamMailboxMember.asMember(ALICE));
    }

    @Test
    void memberRemovedShouldRemoveTheNamedMembers() {
        handle("twake.space.created", created(SPACE, "Sales", member(ALICE, "admin"), member(BOB, "editor")));

        handle("twake.space.member.removed", """
            {"organizationId": "org-1", "organizationDomain": "acme.com", "id": "%s", "members": [{"email": "BOB@acme.com"}]}
            """.formatted(SPACE));

        assertThat(members(teamMailbox("sales")))
            .containsExactly(TeamMailboxMember.asManager(ALICE));
    }

    @Test
    void memberEventOfAnUnknownSpaceShouldBeSkipped() {
        handle("twake.space.member.added", memberEvent(SPACE, member(BOB, "editor")));

        assertThat(Flux.from(teamMailboxRepository.listTeamMailboxes()).collectList().block()).isEmpty();
    }

    @Test
    void deletedShouldCloseTheTeamMailbox() {
        handle("twake.space.created", created(SPACE, "Sales", member(ALICE, "admin")));

        handle("twake.space.deleted", """
            {"organizationId": "org-1", "organizationDomain": "acme.com", "id": "%s"}
            """.formatted(SPACE));

        assertThat(members(teamMailbox("sales"))).isEmpty();
        assertThat(spaceMailbox(teamMailbox("sales")).binding())
            .contains(new SpaceBinding(ORGANIZATION, SPACE, Optional.of(NOW)));
    }

    @Test
    void deletedWithoutDomainShouldCloseTheTeamMailbox() {
        handle("twake.space.created", created(SPACE, "Sales", member(ALICE, "admin")));

        handle("twake.space.deleted", """
            {"id": "%s"}
            """.formatted(SPACE));

        assertThat(spaceMailbox(teamMailbox("sales")).isLive()).isFalse();
    }

    @Test
    void aDeletedSpaceShouldStayDeleted() {
        handle("twake.space.created", created(SPACE, "Sales", member(ALICE, "admin")));
        handle("twake.space.deleted", """
            {"organizationId": "org-1", "organizationDomain": "acme.com", "id": "%s"}
            """.formatted(SPACE));

        handle("twake.space.synced", created(SPACE, "Sales", member(ALICE, "admin")));
        handle("twake.space.member.added", memberEvent(SPACE, member(BOB, "editor")));

        assertThat(members(teamMailbox("sales"))).isEmpty();
        assertThat(Flux.from(teamMailboxRepository.listTeamMailboxes(DOMAIN)).collectList().block())
            .containsExactly(teamMailbox("sales"));
    }

    @Test
    void syncCompletedShouldCloseTheUnlistedSpacesOfTheOrganization() {
        handle("twake.space.created", created(SPACE, "Sales", member(ALICE, "admin")));
        handle("twake.space.created", created(OTHER_SPACE, "Support", member(ALICE, "admin")));
        handle("twake.space.created", """
            {"organizationId": "org-2", "organizationDomain": "acme.com", "id": "33333333-3333-3333-3333-333333333333", "name": "Other org"}
            """);

        handle("twake.space.sync.completed", """
            {"organizationId": "org-1", "organizationDomain": "acme.com", "spaceIds": ["%s"]}
            """.formatted(SPACE));

        assertThat(spaceMailbox(teamMailbox("sales")).isLive()).isTrue();
        assertThat(spaceMailbox(teamMailbox("support")).isLive()).isFalse();
        assertThat(spaceMailbox(teamMailbox("other-org")).isLive()).isTrue();
    }

    @Test
    void validatedDnsShouldRequestASyncOfTheOrganization() {
        testee.handle(DNS_EXCHANGE, "dns.validated", """
            {"organizationId": "org-1", "domain": "acme.com", "mailDnsConfigurationValidated": true}
            """.getBytes(UTF_8)).block();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published).hasSize(1));
        Published request = published.peek();
        assertThat(request.exchange()).isEqualTo(configuration.spaceExchange());
        assertThat(request.routingKey()).isEqualTo("twake.space.sync.requested");
        assertThatJson(request.body()).isEqualTo("""
            {"organizationId": "org-1", "timestamp": "2026-10-09T08:00:00Z"}""");
    }

    @Test
    void notValidatedDnsShouldRequestNothing() throws Exception {
        testee.handle(DNS_EXCHANGE, "dns.validated", """
            {"organizationId": "org-1", "domain": "acme.com", "mailDnsConfigurationValidated": false}
            """.getBytes(UTF_8)).block();
        handle("twake.space.created", created(SPACE, "Sales"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published)
            .extracting(Published::routingKey)
            .containsExactly(SpacePublisher.PROVISIONED));
    }

    @Test
    void malformedEventShouldBeDropped() {
        handle("twake.space.created", """
            {"organizationId": "org-1", "id": "%s", "name": "Sales"}
            """.formatted(SPACE));

        assertThat(Flux.from(teamMailboxRepository.listTeamMailboxes()).collectList().block()).isEmpty();
    }

    @Test
    void nonJsonBodyShouldFail() {
        assertThatThrownBy(() -> testee.handle(configuration.spaceExchange(), "twake.space.created", "not json".getBytes(UTF_8)).block())
            .isNotNull();
    }

    @Nested
    class Purge {
        @Test
        void shouldDeleteTheTeamMailboxesClosedForLongerThanTheRetention() {
            handle("twake.space.created", created(SPACE, "Sales"));
            handle("twake.space.created", created(OTHER_SPACE, "Support"));
            handle("twake.space.deleted", """
                {"organizationDomain": "acme.com", "id": "%s"}
                """.formatted(SPACE));
            clock.setInstant(NOW.plus(Duration.ofDays(31)));

            new SpacePurge(spaceMailboxes, teamMailboxRepository, configuration, clock).purge().block();

            assertThat(Flux.from(teamMailboxRepository.listTeamMailboxes(DOMAIN)).collectList().block())
                .containsExactly(teamMailbox("support"));
        }

        @Test
        void shouldKeepTheTeamMailboxesClosedRecently() {
            handle("twake.space.created", created(SPACE, "Sales"));
            handle("twake.space.deleted", """
                {"organizationDomain": "acme.com", "id": "%s"}
                """.formatted(SPACE));
            clock.setInstant(NOW.plus(Duration.ofDays(29)));

            new SpacePurge(spaceMailboxes, teamMailboxRepository, configuration, clock).purge().block();

            assertThat(Flux.from(teamMailboxRepository.listTeamMailboxes(DOMAIN)).collectList().block())
                .containsExactly(teamMailbox("sales"));
        }
    }

    @Nested
    class Activity {
        @BeforeEach
        void setUp() {
            mailboxManager.getEventBus().register(new SpaceActivityListener(mailboxManager, messageIdManager, spaceMailboxes, publisher, clock));
        }

        @Test
        void deliveryShouldPublishReceived() throws Exception {
            handle("twake.space.created", created(SPACE, "Sales"));
            TeamMailbox teamMailbox = teamMailbox("sales");
            String rootId = spaceMailbox(teamMailbox).rootId().serialize();

            MessageId messageId = append(teamMailbox, teamMailbox.inboxPath(), "Subject: Hello\r\n\r\nbody\r\n", true);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published)
                .filteredOn(event -> event.routingKey().equals("com.twake.mail.message.received.v1"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.messageId()).isEqualTo(rootId + ":" + messageId.serialize() + ":received");
                    assertThatJson(event.body()).isEqualTo("""
                        {
                          "specversion": "1.0",
                          "id": "%s:%s:received",
                          "source": "twake://mail",
                          "type": "com.twake.mail.message.received.v1",
                          "time": "${json-unit.ignore}",
                          "twakeorg": "org-1",
                          "data": {"object": {"type": "message", "id": "%s", "title": "Hello",
                                              "container": {"kind": "mailbox", "id": "%s"}}}
                        }""".formatted(rootId, messageId.serialize(), messageId.serialize(), rootId));
                }));
        }

        @Test
        void sentShouldPublishSentWithNoSubject() throws Exception {
            handle("twake.space.created", created(SPACE, "Sales"));
            TeamMailbox teamMailbox = teamMailbox("sales");

            append(teamMailbox, teamMailbox.sentPath(), "From: alice@acme.com\r\n\r\nbody\r\n", false);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published)
                .filteredOn(event -> event.routingKey().equals("com.twake.mail.message.sent.v1"))
                .singleElement()
                .satisfies(event -> assertThatJson(event.body()).inPath("data.object.title").isEqualTo("(no subject)")));
        }

        @Test
        void mailOfAClosedSpaceShouldNotBeReported() throws Exception {
            handle("twake.space.created", created(SPACE, "Sales"));
            handle("twake.space.deleted", """
                {"organizationDomain": "acme.com", "id": "%s"}
                """.formatted(SPACE));
            TeamMailbox teamMailbox = teamMailbox("sales");
            append(teamMailbox, teamMailbox.inboxPath(), "Subject: Closed\r\n\r\nbody\r\n", true);

            handle("twake.space.created", created(OTHER_SPACE, "Support"));
            TeamMailbox support = teamMailbox("support");
            MessageId sentinel = append(support, support.inboxPath(), "Subject: Sentinel\r\n\r\nbody\r\n", true);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published)
                .filteredOn(event -> event.routingKey().equals("com.twake.mail.message.received.v1"))
                .extracting(Published::messageId)
                .containsExactly(spaceMailbox(support).rootId().serialize() + ":" + sentinel.serialize() + ":received"));
        }

        @Test
        void mailOfATeamMailboxNoSpaceHoldsShouldNotBeReported() throws Exception {
            TeamMailbox unbound = teamMailbox("unbound");
            Mono.from(teamMailboxRepository.createTeamMailbox(unbound)).block();
            append(unbound, unbound.inboxPath(), "Subject: Unbound\r\n\r\nbody\r\n", true);

            handle("twake.space.created", created(SPACE, "Sales"));
            TeamMailbox sales = teamMailbox("sales");
            MessageId sentinel = append(sales, sales.inboxPath(), "Subject: Sentinel\r\n\r\nbody\r\n", true);

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published)
                .filteredOn(event -> event.routingKey().equals("com.twake.mail.message.received.v1"))
                .extracting(Published::messageId)
                .containsExactly(spaceMailbox(sales).rootId().serialize() + ":" + sentinel.serialize() + ":received"));
        }
    }

    @Test
    void consumerShouldApplyEventsPublishedOnTheSpaceExchange() throws Exception {
        ManagedRabbitMQConsumer rabbitMQConsumer = SpaceEventsConsumer.create(publisher, rabbitMQExtension.getRabbitMQ().getConfiguration(),
            configuration, testee);
        rabbitMQConsumer.init();
        try {
            rabbitMQExtension.getSender().send(Mono.just(new OutboundMessage(configuration.spaceExchange(), "twake.space.created",
                created(SPACE, "Sales", member(ALICE, "admin")).getBytes(UTF_8)))).block();

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(spaceMailboxes.read(teamMailbox("sales")).blockOptional())
                .hasValueSatisfying(spaceMailbox -> assertThat(spaceMailbox.isLiveFor(SPACE)).isTrue()));
        } finally {
            rabbitMQConsumer.close();
        }
    }

    @Test
    void readOnlyPredicateShouldCoverTheSpaceKeys() {
        assertThat(SpaceAnnotations.KEYS).allMatch(SpaceAnnotations.READ_ONLY::isReadOnly);
        assertThat(SpaceAnnotations.READ_ONLY.isReadOnly(new MailboxAnnotationKey("/shared/comment"))).isFalse();
    }

    private void handle(String routingKey, String body) {
        testee.handle(configuration.spaceExchange(), routingKey, body.getBytes(UTF_8)).block();
    }

    private static String created(String spaceId, String name, String... members) {
        return """
            {"organizationId": "org-1", "organizationDomain": "acme.com", "id": "%s", "name": "%s", "members": [%s], "timestamp": "2026-10-09T08:00:00Z"}
            """.formatted(spaceId, name, String.join(",", members));
    }

    private static String memberEvent(String spaceId, String... members) {
        return """
            {"organizationId": "org-1", "organizationDomain": "acme.com", "id": "%s", "members": [%s], "timestamp": "2026-10-09T08:00:00Z"}
            """.formatted(spaceId, String.join(",", members));
    }

    private static String member(Username username, String role) {
        return """
            {"uuid": "%s", "email": "%s", "role": "%s"}""".formatted(UUID.randomUUID(), username.asString(), role);
    }

    private static TeamMailbox teamMailbox(String name) {
        return OptionConverters.toJava(TeamMailbox.fromJava(DOMAIN, name)).orElseThrow();
    }

    private SpaceMailbox spaceMailbox(TeamMailbox teamMailbox) {
        return spaceMailboxes.read(teamMailbox).blockOptional().orElseThrow();
    }

    private List<TeamMailboxMember> members(TeamMailbox teamMailbox) {
        return Flux.from(teamMailboxRepository.listMembers(teamMailbox)).collectList().block();
    }

    private MemoryDomainList domainListWith(Domain domain) {
        try {
            MemoryDomainList domainList = new MemoryDomainList(new InMemoryDNSService());
            domainList.configure(DomainListConfiguration.DEFAULT);
            domainList.addDomain(domain);
            return domainList;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MessageId append(TeamMailbox teamMailbox, MailboxPath path, String message, boolean isDelivery) throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(teamMailbox.owner());
        return mailboxManager.getMailbox(path, session)
            .appendMessage(MessageManager.AppendCommand.builder()
                .isDelivery(isDelivery)
                .build(message), session)
            .getId()
            .getMessageId();
    }

    private ExchangeSpecification topicExchange(String name) {
        return ExchangeSpecification.exchange(name)
            .type(BuiltinExchangeType.TOPIC.getType())
            .durable(true);
    }
}
