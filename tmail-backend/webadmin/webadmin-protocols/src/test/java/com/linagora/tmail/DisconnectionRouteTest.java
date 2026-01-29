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

package com.linagora.tmail;

import static io.restassured.RestAssured.given;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.james.DisconnectorNotifier;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.Username;
import org.apache.james.events.EventBusId;
import org.apache.james.events.EventBusName;
import org.apache.james.events.EventSerializer;
import org.apache.james.events.EventSerializersAggregator;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.NamingStrategy;
import org.apache.james.events.RabbitMQEventBus;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.RoutingKeyConverter;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.webadmin.ProtocolServerRoutes;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.disconnector.DisconnectionEventListener;
import com.linagora.tmail.disconnector.DisconnectionRequestedEventSerializer;
import com.linagora.tmail.disconnector.DisconnectorRegistrationKey;
import com.linagora.tmail.disconnector.EventBusDisconnectorNotifier;

import io.restassured.specification.RequestSpecification;
import reactor.core.publisher.Mono;

public class DisconnectionRouteTest {
    private record ServerWithEventBus(WebAdminServer server, RabbitMQEventBus eventBus) {
    }

    public static final String BOB = "bob@domain.tld";
    public static final Username BOB_USERNAME = Username.of(BOB);
    public static final Username ALICE_USERNAME = Username.of("alice@domain.tld");
    public static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.STRONG);

    private WebAdminServer webAdminServer1;
    private WebAdminServer webAdminServer2;
    private RabbitMQEventBus eventBus1;
    private RabbitMQEventBus eventBus2;

    private static final NamingStrategy NAMING_STRATEGY = new NamingStrategy(new EventBusName("tmail-event-bus"));
    private static final RoutingKeyConverter ROUTING_KEY_CONVERTER = new RoutingKeyConverter(ImmutableSet.of(new DisconnectorRegistrationKey.Factory()));
    private static final EventSerializer EVENT_SERIALIZER = new EventSerializersAggregator(ImmutableSet.of(new DisconnectionRequestedEventSerializer()));

    private ServerWithEventBus webAdminServer(List<Username> initConnectedUsers) throws URISyntaxException {
        TestConnectionDisconnector testConnectionDisconnector = new TestConnectionDisconnector(initConnectedUsers);
        RabbitMQEventBus.Configurations rabbitMQConfigurations = new RabbitMQEventBus.Configurations(rabbitMQExtension.getRabbitMQ().getConfiguration(), RetryBackoffConfiguration.DEFAULT);
        RabbitMQEventBus eventBus = new RabbitMQEventBus(NAMING_STRATEGY,
            rabbitMQExtension.getSender(),
            rabbitMQExtension.getReceiverProvider(),
            EVENT_SERIALIZER,
            ROUTING_KEY_CONVERTER,
            new MemoryEventDeadLetters(),
            new RecordingMetricFactory(),
            rabbitMQExtension.getRabbitChannelPool(),
            EventBusId.random(),
            rabbitMQConfigurations);

        eventBus.start();
        Mono.from(eventBus.register(new DisconnectionEventListener(testConnectionDisconnector), DisconnectorRegistrationKey.KEY)).block();

        DisconnectorNotifier disconnectorNotifier = new EventBusDisconnectorNotifier(eventBus);
        ProtocolServerRoutes protocolServerRoutes = new ProtocolServerRoutes(Set.of(), disconnectorNotifier, testConnectionDisconnector);
        return new ServerWithEventBus(WebAdminUtils.createWebAdminServer(protocolServerRoutes).start(), eventBus);
    }

    private RequestSpecification requestSpecification(WebAdminServer webAdminServer) {
        return WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("/servers")
            .build();
    }

    @AfterEach
    void tearDown() {
        if (webAdminServer1 != null) {
            webAdminServer1.destroy();
        }
        if (webAdminServer2 != null) {
            webAdminServer2.destroy();
        }
        if (eventBus1 != null) {
            eventBus1.stop();
        }
        if (eventBus2 != null) {
            eventBus2.stop();
        }
    }

    @Test
    void bobShouldBeDisconnectedOnConnectedServer() throws Exception {
        // Given Bob is connected on server 1
        ServerWithEventBus server1 = webAdminServer(List.of(BOB_USERNAME));
        webAdminServer1 = server1.server();
        eventBus1 = server1.eventBus();

        ServerWithEventBus server2 = webAdminServer(List.of());
        webAdminServer2 = server2.server();
        eventBus2 = server2.eventBus();

        // Verify Bob is connected on server 1
        given()
            .spec(requestSpecification(webAdminServer1))
            .get("/connectedUsers")
        .then()
            .statusCode(200)
            .body("", containsInAnyOrder(BOB));

        // When call disconnection bob on server 2
        given()
            .spec(requestSpecification(webAdminServer2))
            .delete("/channels/" + BOB)
        .then()
            .statusCode(204);

        // Then Bob should be disconnected on server 1
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() ->
                given()
                    .spec(requestSpecification(webAdminServer1))
                    .get("/connectedUsers")
                .then()
                    .statusCode(200)
                    .body("", not(containsInAnyOrder(BOB))));
    }

    @Test
    void bobShouldBeDisconnectedOnAllServerWhenAskedOnAnyServer() throws Exception {
        // Given Bob is connected on server 1 and server 2
        ServerWithEventBus server1 = webAdminServer(List.of(BOB_USERNAME));
        webAdminServer1 = server1.server();
        eventBus1 = server1.eventBus();

        ServerWithEventBus server2 = webAdminServer(List.of(BOB_USERNAME));
        webAdminServer2 = server2.server();
        eventBus2 = server2.eventBus();

        // Verify Bob is connected on server 1
        given()
            .spec(requestSpecification(webAdminServer1))
            .get("/connectedUsers")
        .then()
            .statusCode(200)
            .body("", containsInAnyOrder(BOB));

        // Verify Bob is connected on server 2
        given()
            .spec(requestSpecification(webAdminServer2))
            .get("/connectedUsers")
        .then()
            .statusCode(200)
            .body("", containsInAnyOrder(BOB));

        // When call disconnection bob on any server
        given()
            .spec(requestSpecification(webAdminServer1))
            .delete("/channels/" + BOB)
        .then()
            .statusCode(204);

        // Then Bob should be disconnected on all server
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                given()
                    .spec(requestSpecification(webAdminServer1))
                    .get("/connectedUsers")
                .then()
                    .statusCode(200)
                    .body("", not(containsInAnyOrder(BOB)));

                given()
                    .spec(requestSpecification(webAdminServer2))
                    .get("/connectedUsers")
                .then()
                    .statusCode(200)
                    .body("", not(containsInAnyOrder(BOB)));
            });
    }

    @Test
    void bobShouldNotBeDisconnectedWhenDisconnectAlice() throws Exception {
        // Given Bob is connected on server 1
        ServerWithEventBus server1 = webAdminServer(List.of(BOB_USERNAME));
        webAdminServer1 = server1.server();
        eventBus1 = server1.eventBus();

        ServerWithEventBus server2 = webAdminServer(List.of());
        webAdminServer2 = server2.server();
        eventBus2 = server2.eventBus();

        // Verify Bob is connected on server 1
        given()
            .spec(requestSpecification(webAdminServer1))
            .get("/connectedUsers")
        .then()
            .statusCode(200)
            .body("", containsInAnyOrder(BOB));

        // When call disconnection Alice on server 2
        given()
            .spec(requestSpecification(webAdminServer2))
            .delete("/channels/" + ALICE_USERNAME.asString())
        .then()
            .statusCode(204);

        // Then Bob should not be disconnected on server 1
        TimeUnit.SECONDS.sleep(1);
        given()
            .spec(requestSpecification(webAdminServer1))
            .get("/connectedUsers")
        .then()
            .statusCode(200)
            .body("", containsInAnyOrder(BOB));
    }
}