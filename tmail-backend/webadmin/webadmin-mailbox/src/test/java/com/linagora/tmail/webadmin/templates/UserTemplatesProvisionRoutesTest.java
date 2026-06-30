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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import jakarta.mail.Flags;

class UserTemplatesProvisionRoutesTest {
    private static final Domain DOMAIN = Domain.of("example.com");
    private static final Username SOURCE_USER = Username.fromLocalPartWithDomain("templates", DOMAIN);
    private static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    private static final String TEMPLATES = DefaultMailboxes.TEMPLATES;
    private static final String CUSTOM_FOLDER = "Templates.Marketing";

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private StoreMailboxManager mailboxManager;
    private SessionProvider sessionProvider;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        sessionProvider = mailboxManager.getSessionProvider();

        MemoryDomainList domainList = new MemoryDomainList(mock(DNSService.class));
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(DOMAIN);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(SOURCE_USER, "anyPassword");
        usersRepository.addUser(BOB, "anyPassword");

        TemplatesProvisionService provisionService = new TemplatesProvisionService(mailboxManager, usersRepository);
        createFolderWithMessage(SOURCE_USER, TEMPLATES);
        createFolderWithMessage(SOURCE_USER, CUSTOM_FOLDER);

        taskManager = new MemoryTaskManager(new Hostname("foo"));

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new UserTemplatesProvisionRoutes(taskManager, provisionService, new JsonTransformer()),
                new TasksRoutes(taskManager, new JsonTransformer(),
                    DTOConverter.of(UserTemplatesProvisionTaskAdditionalInformationDTO.module())))
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
    void postShouldReturn201WithTaskId() {
        given()
            .queryParam("action", "provision")
            .queryParam("from", "templates@example.com")
        .when()
            .post("/users/bob@example.com/templates")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void taskShouldCompleteSuccessfully() {
        String taskId = given()
            .queryParam("action", "provision")
            .queryParam("from", "templates@example.com")
            .queryParam("folderName", "Templates.Marketing")
        .when()
            .post("/users/bob@example.com/templates")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .extract()
            .jsonPath()
            .getString("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", Matchers.is("completed"))
            .body("type", Matchers.is(UserTemplatesProvisionTask.TASK_TYPE.asString()))
            .body("additionalInformation.sourceUser", Matchers.is("templates@example.com"))
            .body("additionalInformation.targetUser", Matchers.is("bob@example.com"))
            .body("additionalInformation.folderName", Matchers.is("Templates.Marketing"))
            .body("additionalInformation.processedUsers", Matchers.is(1))
            .body("additionalInformation.appliedTemplates", Matchers.is(1));
    }

    @Test
    void postShouldReturn400WhenActionIsUnknown() {
        given()
            .queryParam("action", "unknown")
            .queryParam("from", "templates@example.com")
        .when()
            .post("/users/bob@example.com/templates")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void postShouldReturn400WhenFromIsMissing() {
        given()
            .queryParam("action", "provision")
        .when()
            .post("/users/bob@example.com/templates")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void postShouldReturn400WhenOverwriteExistingIsInvalid() {
        given()
            .queryParam("action", "provision")
            .queryParam("from", "templates@example.com")
            .queryParam("overwriteExisting", "invalid")
        .when()
            .post("/users/bob@example.com/templates")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void postShouldReturn404WhenTargetUserDoesNotExist() {
        given()
            .queryParam("action", "provision")
            .queryParam("from", "templates@example.com")
        .when()
            .post("/users/unknown@example.com/templates")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void postShouldReturn404WhenSourceFolderDoesNotExist() {
        given()
            .queryParam("action", "provision")
            .queryParam("from", "nofolder@example.com")
        .when()
            .post("/users/bob@example.com/templates")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    private void createFolderWithMessage(Username user, String folderName) throws Exception {
        MailboxSession session = sessionProvider.createSystemSession(user);
        mailboxManager.createMailbox(MailboxPath.forUser(user, folderName), session);
        MessageManager messageManager = mailboxManager.getMailbox(MailboxPath.forUser(user, folderName), session);
        String eml = "Message-ID: <t1@example.com>\r\nSubject: A template\r\n\r\nFirst template";
        messageManager.appendMessage(new ByteArrayInputStream(eml.getBytes(StandardCharsets.UTF_8)),
            new Date(), session, false, new Flags());
    }
}
