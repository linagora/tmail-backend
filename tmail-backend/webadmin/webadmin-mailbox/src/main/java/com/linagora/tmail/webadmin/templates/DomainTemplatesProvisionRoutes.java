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

import static org.apache.james.webadmin.Constants.SEPARATOR;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import spark.Request;
import spark.Service;

public class DomainTemplatesProvisionRoutes implements Routes {
    private static final String DOMAINS_BASE = SEPARATOR + "domains";
    private static final String DOMAIN_PARAM = ":domain";
    private static final String TEMPLATES_PATH = DOMAINS_BASE + SEPARATOR + DOMAIN_PARAM + SEPARATOR + "templates";

    private final TaskManager taskManager;
    private final DomainList domainList;
    private final TemplatesProvisionService provisionService;
    private final JsonTransformer jsonTransformer;

    @Inject
    public DomainTemplatesProvisionRoutes(TaskManager taskManager, DomainList domainList,
                                          TemplatesProvisionService provisionService, JsonTransformer jsonTransformer) {
        this.taskManager = taskManager;
        this.domainList = domainList;
        this.provisionService = provisionService;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return DOMAINS_BASE;
    }

    @Override
    public void define(Service service) {
        TaskFromRequest taskFromRequest = this::createTask;
        service.post(TEMPLATES_PATH, taskFromRequest.asRoute(taskManager), jsonTransformer);
    }

    private DomainTemplatesProvisionTask createTask(Request request) {
        TemplatesProvisionRequest.assertProvisionAction(request);
        Domain domain = extractDomain(request);
        assertDomainExists(domain);
        Username sourceUser = TemplatesProvisionRequest.parseSourceUser(request);
        String folderName = TemplatesProvisionRequest.parseFolderName(request);
        ProvisionOptions options = TemplatesProvisionRequest.parseOptions(request);
        int usersPerSecond = TemplatesProvisionRequest.parseUsersPerSecond(request);
        assertSourceFolderExists(sourceUser, folderName);
        return new DomainTemplatesProvisionTask(provisionService, domain, sourceUser, folderName, options, usersPerSecond);
    }

    private Domain extractDomain(Request request) {
        try {
            return Domain.of(request.params(DOMAIN_PARAM));
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid domain: '%s'", request.params(DOMAIN_PARAM))
                .haltError();
        }
    }

    private void assertDomainExists(Domain domain) {
        try {
            if (!domainList.containsDomain(domain)) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("Domain '%s' does not exist", domain.asString())
                    .haltError();
            }
        } catch (DomainListException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .type(ErrorResponder.ErrorType.SERVER_ERROR)
                .message("Error while checking domain existence")
                .cause(e)
                .haltError();
        }
    }

    private void assertSourceFolderExists(Username sourceUser, String folderName) {
        if (Boolean.FALSE.equals(provisionService.sourceFolderExists(sourceUser, folderName).block())) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message("Folder '%s' of source user '%s' does not exist", folderName, sourceUser.asString())
                .haltError();
        }
    }
}
