/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                  *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.webadmin.vault;

import static org.apache.james.webadmin.Constants.SEPARATOR;

import jakarta.inject.Inject;

import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Mono;
import spark.Request;
import spark.Service;

public class TeamMailboxDeletedMessagesVaultRoutes implements Routes {

    public static final String ROOT_PATH = "deletedMessages" + SEPARATOR + "teamMailbox";
    private static final String TEAM_MAILBOX_ADDRESS_PARAM = ":teamMailboxAddress";
    static final String TEAM_MAILBOX_PATH = ROOT_PATH + SEPARATOR + TEAM_MAILBOX_ADDRESS_PARAM;
    private static final TaskRegistrationKey RESTORE_REGISTRATION_KEY = TaskRegistrationKey.of("restore");

    private final TeamMailboxRestoreService restoreService;
    private final TeamMailboxRepository teamMailboxRepository;
    private final JsonTransformer jsonTransformer;
    private final TaskManager taskManager;

    @Inject
    public TeamMailboxDeletedMessagesVaultRoutes(TeamMailboxRestoreService restoreService,
                                                  TeamMailboxRepository teamMailboxRepository,
                                                  JsonTransformer jsonTransformer,
                                                  TaskManager taskManager) {
        this.restoreService = restoreService;
        this.teamMailboxRepository = teamMailboxRepository;
        this.jsonTransformer = jsonTransformer;
        this.taskManager = taskManager;
    }

    @Override
    public String getBasePath() {
        return ROOT_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(TEAM_MAILBOX_PATH,
            TaskFromRequestRegistry.builder()
                .register(RESTORE_REGISTRATION_KEY, this::restore)
                .buildAsRoute(taskManager),
            jsonTransformer);
    }

    private Task restore(Request request) {
        TeamMailbox teamMailbox = extractTeamMailbox(request);
        validateTeamMailboxExists(teamMailbox);
        return new TeamMailboxVaultRestoreTask(restoreService, teamMailbox);
    }

    private TeamMailbox extractTeamMailbox(Request request) {
        String address = request.params(TEAM_MAILBOX_ADDRESS_PARAM);
        return TeamMailbox.fromString(address)
            .fold(e -> {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                    .message("Invalid team mailbox address '%s': %s", address, e.getMessage())
                    .haltError();
            }, x -> x);
    }

    private void validateTeamMailboxExists(TeamMailbox teamMailbox) {
        if (!Boolean.TRUE.equals(Mono.from(teamMailboxRepository.exists(teamMailbox)).block())) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message("Team mailbox '%s' does not exist", teamMailbox.asMailAddress().asString())
                .haltError();
        }
    }
}
