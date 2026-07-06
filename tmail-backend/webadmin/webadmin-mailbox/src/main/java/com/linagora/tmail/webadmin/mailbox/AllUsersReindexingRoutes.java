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

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.indexer.ReIndexer.RunningOptions;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.routes.ReindexingRunningOptionsParser;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import reactor.core.publisher.Flux;
import spark.Request;
import spark.Response;
import spark.Service;

public class AllUsersReindexingRoutes implements Routes {
    private static final String BASE_PATH = "/users";
    private static final String ACTION_PARAM = "action";
    private static final String REINDEX_ACTION = "reindex";

    private final UsersRepository usersRepository;
    private final ReIndexer reIndexer;
    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;

    @Inject
    public AllUsersReindexingRoutes(UsersRepository usersRepository, ReIndexer reIndexer,
                                    TaskManager taskManager, JsonTransformer jsonTransformer) {
        this.usersRepository = usersRepository;
        this.reIndexer = reIndexer;
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(BASE_PATH, this::reIndexAllUsers, jsonTransformer);
    }

    private Map<String, String> reIndexAllUsers(Request request, Response response) {
        assertReindexAction(request);
        RunningOptions runningOptions = parseRunningOptions(request);

        Map<String, String> taskIds = Flux.from(usersRepository.listReactive())
            .collectMap(Username::asString,
                username -> scheduleReindexingTask(username, runningOptions),
                LinkedHashMap::new)
            .block();

        response.status(HttpStatus.CREATED_201);
        return taskIds;
    }

    private String scheduleReindexingTask(Username username, RunningOptions runningOptions) {
        try {
            return taskManager.submit(reIndexer.reIndex(username, runningOptions)).asString();
        } catch (MailboxException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .type(ErrorType.SERVER_ERROR)
                .message("Error while scheduling reindexing task for user " + username.asString())
                .cause(e)
                .haltError();
        }
    }

    private void assertReindexAction(Request request) {
        String action = request.queryParams(ACTION_PARAM);
        if (!REINDEX_ACTION.equals(action)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Invalid or missing 'action' query parameter. Only 'reindex' is supported.")
                .haltError();
        }
    }

    private RunningOptions parseRunningOptions(Request request) {
        try {
            return ReindexingRunningOptionsParser.parse(request);
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message(e.getMessage())
                .cause(e)
                .haltError();
        }
    }
}
