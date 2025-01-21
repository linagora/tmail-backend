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

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.james.DisconnectorNotifier;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.Username;
import org.apache.james.protocols.webadmin.ProtocolServerRoutes;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;

import io.restassured.specification.RequestSpecification;

public class DisconnectionRouteTest {

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

    private WebAdminServer webAdminServer(List<Username> initConnectedUsers) {
        TestConnectionDisconnector testConnectionDisconnector = new TestConnectionDisconnector(initConnectedUsers);
        DisconnectorNotifier.InVMDisconnectorNotifier inVmDisconnectorNotifier = new DisconnectorNotifier.InVMDisconnectorNotifier(testConnectionDisconnector);
        DisconnectorRequestSerializer disconnectorRequestSerializer = new DisconnectorRequestSerializer();
        RabbitMQDisconnectorConsumer rabbitMQDisconnectorConsumer = new RabbitMQDisconnectorConsumer(rabbitMQExtension.getReceiverProvider(),
            inVmDisconnectorNotifier,
            disconnectorRequestSerializer,
            UUID.randomUUID().toString());

        RabbitMQDisconnectorOperator rabbitMQDisconnectorOperator = new RabbitMQDisconnectorOperator(rabbitMQExtension.getSender(),
            Throwing.supplier(() -> rabbitMQExtension.getRabbitMQ().getConfiguration()).get(), rabbitMQDisconnectorConsumer);

        rabbitMQDisconnectorOperator.init();

        DisconnectorNotifier disconnectorNotifier = new RabbitMQDisconnectorNotifier(rabbitMQExtension.getSender(), disconnectorRequestSerializer);
        ProtocolServerRoutes protocolServerRoutes = new ProtocolServerRoutes(Set.of(), disconnectorNotifier, testConnectionDisconnector);
        return WebAdminUtils.createWebAdminServer(protocolServerRoutes).start();
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
    }

    @Test
    void bobShouldBeDisconnectedOnConnectedServer() {
        // Given Bob is connected on server 1
        webAdminServer1 = webAdminServer(List.of(BOB_USERNAME));
        webAdminServer2 = webAdminServer(List.of());

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
    void bobShouldBeDisconnectedOnAllServerWhenAskedOnAnyServer() {
        // Given Bob is connected on server 1 and server 2
        webAdminServer1 = webAdminServer(List.of(BOB_USERNAME));
        webAdminServer2 = webAdminServer(List.of(BOB_USERNAME));

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
    void bobShouldNotDisconnectedWhenDisconnectAlice() throws InterruptedException {
        // Given Bob is connected on server 1
        webAdminServer1 = webAdminServer(List.of(BOB_USERNAME));
        webAdminServer2 = webAdminServer(List.of());

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