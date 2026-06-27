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

import org.apache.james.core.Username;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import spark.Request;
import spark.Service;

public class UserTemplatesProvisionRoutes implements Routes {
    private static final String USERS_BASE = SEPARATOR + "users";
    private static final String USERNAME_PARAM = ":username";
    private static final String TEMPLATES_PATH = USERS_BASE + SEPARATOR + USERNAME_PARAM + SEPARATOR + "templates";

    private final TaskManager taskManager;
    private final TemplatesProvisionService provisionService;
    private final JsonTransformer jsonTransformer;

    @Inject
    public UserTemplatesProvisionRoutes(TaskManager taskManager, TemplatesProvisionService provisionService,
                                        JsonTransformer jsonTransformer) {
        this.taskManager = taskManager;
        this.provisionService = provisionService;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return USERS_BASE;
    }

    @Override
    public void define(Service service) {
        TaskFromRequest taskFromRequest = this::createTask;
        service.post(TEMPLATES_PATH, taskFromRequest.asRoute(taskManager), jsonTransformer);
    }

    private UserTemplatesProvisionTask createTask(Request request) {
        TemplatesProvisionRequest.assertProvisionAction(request);
        Username targetUser = TemplatesProvisionRequest.parseUsername(request.params(USERNAME_PARAM));
        Username sourceUser = TemplatesProvisionRequest.parseSourceUser(request);
        String folderName = TemplatesProvisionRequest.parseFolderName(request);
        ProvisionOptions options = TemplatesProvisionRequest.parseOptions(request);
        assertSourceFolderExists(sourceUser, folderName);
        return new UserTemplatesProvisionTask(provisionService, sourceUser, targetUser, folderName, options);
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
