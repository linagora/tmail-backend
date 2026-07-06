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

package com.linagora.tmail.webadmin.mailbox;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.indexer.ReIndexer.RunningOptions;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.Task;
import org.apache.james.task.TaskType;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;

class AllUsersReindexingRoutesTest {
    private static final Domain DOMAIN = Domain.of("example.com");
    private static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    private static final Username ALICE = Username.fromLocalPartWithDomain("alice", DOMAIN);

    private static final Task NOOP_TASK = new Task() {
        @Override
        public Result run() {
            return Result.COMPLETED;
        }

        @Override
        public TaskType type() {
            return TaskType.of("test-reindex");
        }
    };

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private MemoryUsersRepository usersRepository;
    private ReIndexer reIndexer;

    @BeforeEach
    void setUp() throws Exception {
        MemoryDomainList domainList = new MemoryDomainList(mock(DNSService.class));
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(DOMAIN);
        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(BOB, "anyPassword");
        usersRepository.addUser(ALICE, "anyPassword");

        reIndexer = mock(ReIndexer.class);
        when(reIndexer.reIndex(any(Username.class), any(RunningOptions.class))).thenReturn(NOOP_TASK);

        taskManager = new MemoryTaskManager(new Hostname("foo"));

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new AllUsersReindexingRoutes(usersRepository, reIndexer, taskManager, new JsonTransformer()))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void postShouldReturn201WithTaskIdPerUser() {
        Map<String, String> taskIds = given()
            .queryParam("action", "reindex")
        .when()
            .post("/users")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .extract()
            .jsonPath()
            .getMap(".");

        assertThat(taskIds).containsOnlyKeys(BOB.asString(), ALICE.asString());
        assertThat(taskIds.values()).doesNotContainNull();
    }

    @Test
    void postShouldScheduleOneReindexingTaskPerUser() throws Exception {
        given()
            .queryParam("action", "reindex")
        .when()
            .post("/users")
        .then()
            .statusCode(HttpStatus.CREATED_201);

        verify(reIndexer).reIndex(eq(BOB), any(RunningOptions.class));
        verify(reIndexer).reIndex(eq(ALICE), any(RunningOptions.class));
    }

    @Test
    void postShouldReturnEmptyMapWhenNoUser() throws Exception {
        usersRepository.removeUser(BOB);
        usersRepository.removeUser(ALICE);

        Map<String, String> taskIds = given()
            .queryParam("action", "reindex")
        .when()
            .post("/users")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .extract()
            .jsonPath()
            .getMap(".");

        assertThat(taskIds).isEmpty();
    }

    @Test
    void postShouldReturn400WhenActionMissing() {
        when()
            .post("/users")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);

        verifyNoInteractions(reIndexer);
    }

    @Test
    void postShouldReturn400WhenActionUnknown() {
        given()
            .queryParam("action", "unknown")
        .when()
            .post("/users")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);

        verifyNoInteractions(reIndexer);
    }

    @Test
    void postShouldReturn400WhenMessagesPerSecondIsInvalid() {
        given()
            .queryParam("action", "reindex")
            .queryParam("messagesPerSecond", "abc")
        .when()
            .post("/users")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }
}
