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

package com.linagora.tmail.webadmin.data;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.DurationParser;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.linagora.tmail.tiering.UserDataTieringService;

import spark.Request;
import spark.Service;

public class UserDataTieringRoutes implements Routes {

    private static final String BASE_PATH = "/users";
    private static final String USERNAME_PARAM = ":username";
    private static final String DATA_PATH = BASE_PATH + "/" + USERNAME_PARAM + "/data";
    private static final String TIERING_PARAM = "tiering";
    private static final String MESSAGES_PER_SECOND_PARAM = "messagesPerSecond";

    private final UserDataTieringService tieringService;
    private final TaskManager taskManager;
    private final UsersRepository usersRepository;
    private final JsonTransformer jsonTransformer;

    @Inject
    public UserDataTieringRoutes(UserDataTieringService tieringService, TaskManager taskManager, UsersRepository usersRepository, JsonTransformer jsonTransformer) {
        this.tieringService = tieringService;
        this.taskManager = taskManager;
        this.usersRepository = usersRepository;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        TaskFromRequest taskFromRequest = this::createTask;
        service.post(DATA_PATH, taskFromRequest.asRoute(taskManager), jsonTransformer);
    }

    private UserDataTieringTask createTask(Request request) throws UsersRepositoryException {
        Username username = parseUsername(request.params(USERNAME_PARAM));
        assertUserExists(username);
        Duration tiering = parseTiering(request.queryParams(TIERING_PARAM));
        RunningOptions runningOptions = parseMessagesPerSecond(request.queryParams(MESSAGES_PER_SECOND_PARAM));
        return new UserDataTieringTask(tieringService, username, tiering, runningOptions);
    }

    private RunningOptions parseMessagesPerSecond(String rawMessagesPerSecond) {
        if (rawMessagesPerSecond == null) {
            return RunningOptions.DEFAULT;
        }
        try {
            return new RunningOptions(Integer.parseInt(rawMessagesPerSecond));
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid messagesPerSecond value '" + rawMessagesPerSecond + "'. Expected a strictly positive integer.")
                .haltError();
        }
    }

    private Username parseUsername(String rawUsername) {
        try {
            return Username.of(rawUsername);
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid username: " + rawUsername)
                .haltError();
        }
    }

    private void assertUserExists(Username username) throws UsersRepositoryException {
        if (!usersRepository.contains(username)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message("User '%s' does not exist", username.asString())
                .haltError();
        }
    }

    private Duration parseTiering(String rawTiering) {
        if (rawTiering == null || rawTiering.isBlank()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("'tiering' query parameter is required (e.g. tiering=30d)")
                .haltError();
        }
        Duration tiering;
        try {
            tiering = DurationParser.parse(rawTiering, ChronoUnit.DAYS);
        } catch (IllegalArgumentException e) {
            throw invalidTiering(rawTiering);
        }
        if (tiering.isZero() || tiering.isNegative()) {
            throw invalidTiering(rawTiering);
        }
        return tiering;
    }

    private RuntimeException invalidTiering(String rawTiering) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
            .message("Invalid tiering value '" + rawTiering + "'. Expected a strictly positive duration like '30d', '7d', '24h'.")
            .haltError();
    }
}
