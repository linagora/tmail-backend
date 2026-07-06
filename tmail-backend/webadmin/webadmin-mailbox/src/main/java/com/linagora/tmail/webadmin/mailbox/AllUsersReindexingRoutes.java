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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import spark.Request;
import spark.Response;
import spark.Service;

public class AllUsersReindexingRoutes implements Routes {
    private static final Logger LOGGER = LoggerFactory.getLogger(AllUsersReindexingRoutes.class);

    private record ReindexingResult(String username, String taskId, boolean succeeded) {
        static ReindexingResult success(String username, String taskId) {
            return new ReindexingResult(username, taskId, true);
        }

        static ReindexingResult errored(String username) {
            return new ReindexingResult(username, null, false);
        }
    }

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

    private Map<String, Object> reIndexAllUsers(Request request, Response response) {
        assertReindexAction(request);
        RunningOptions runningOptions = parseRunningOptions(request);

        List<ReindexingResult> results = Flux.from(usersRepository.listReactive())
            .publishOn(Schedulers.boundedElastic())
            .map(username -> scheduleReindexingTask(username, runningOptions))
            .collectList()
            .block();

        Map<String, String> taskIds = results.stream()
            .filter(ReindexingResult::succeeded)
            .collect(Collectors.toMap(ReindexingResult::username, ReindexingResult::taskId,
                (first, second) -> first, LinkedHashMap::new));
        List<String> erroredUsers = results.stream()
            .filter(result -> !result.succeeded())
            .map(ReindexingResult::username)
            .collect(Collectors.toList());

        response.status(HttpStatus.CREATED_201);
        return Map.of("taskIds", taskIds, "erroredUsers", erroredUsers);
    }

    private ReindexingResult scheduleReindexingTask(Username username, RunningOptions runningOptions) {
        try {
            String taskId = taskManager.submit(reIndexer.reIndex(username, runningOptions)).asString();
            return ReindexingResult.success(username.asString(), taskId);
        } catch (MailboxException e) {
            LOGGER.error("Error while scheduling reindexing task for user {}", username.asString(), e);
            return ReindexingResult.errored(username.asString());
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
