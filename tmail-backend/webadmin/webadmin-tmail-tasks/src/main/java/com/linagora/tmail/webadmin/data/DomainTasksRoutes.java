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

import static org.apache.james.webadmin.Constants.SEPARATOR;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.json.DTOConverter;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskNotFoundException;
import org.apache.james.util.DurationParser;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.DTOModuleInjections;
import org.apache.james.webadmin.dto.ExecutionDetailsDto;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import spark.Request;
import spark.Response;
import spark.Service;

public class DomainTasksRoutes implements Routes {
    private static final String DOMAIN_PARAM = ":domain";
    private static final String TASK_ID_PARAM = ":taskId";
    private static final String DOMAINS_PATH = SEPARATOR + "domains";
    public static final String BASE_PATH = DOMAINS_PATH + SEPARATOR + DOMAIN_PARAM + SEPARATOR + "tasks";
    private static final String TASK_PATH = BASE_PATH + SEPARATOR + TASK_ID_PARAM;
    private static final String AWAIT_PATH = TASK_PATH + SEPARATOR + "await";
    private static final Duration MAXIMUM_AWAIT_TIMEOUT = Duration.ofDays(365);

    private final TaskManager taskManager;
    private final DomainList domainList;
    private final Set<TaskBelongsToDomainPredicate> predicates;
    private final DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter;
    private final JsonTransformer jsonTransformer;

    @Inject
    public DomainTasksRoutes(TaskManager taskManager,
                             DomainList domainList,
                             Set<TaskBelongsToDomainPredicate> predicates,
                             @Named(DTOModuleInjections.WEBADMIN_DTO) DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO> additionalInformationConverter,
                             JsonTransformer jsonTransformer) {
        this.taskManager = taskManager;
        this.domainList = domainList;
        this.predicates = predicates;
        this.additionalInformationConverter = additionalInformationConverter;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return DOMAINS_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(TASK_PATH, this::getTask, jsonTransformer);
        service.get(AWAIT_PATH, this::awaitTask, jsonTransformer);
        service.delete(TASK_PATH, this::cancelTask, jsonTransformer);
    }

    private Object getTask(Request request, Response response) {
        Domain domain = extractAndValidateDomain(request);
        TaskId taskId = extractTaskId(request);
        return respondStatus(domain, taskId, () -> taskManager.getExecutionDetails(taskId));
    }

    private Object awaitTask(Request request, Response response) {
        Domain domain = extractAndValidateDomain(request);
        TaskId taskId = extractTaskId(request);
        Duration timeout = extractTimeout(request);
        TaskExecutionDetails currentDetails = getExecutionDetails(taskId);
        checkBelongsToDomain(domain, currentDetails, taskId);
        TaskExecutionDetails finalDetails = awaitTask(taskId, timeout);
        return ExecutionDetailsDto.from(additionalInformationConverter, finalDetails);
    }

    private Object cancelTask(Request request, Response response) {
        Domain domain = extractAndValidateDomain(request);
        TaskId taskId = extractTaskId(request);
        TaskExecutionDetails details = getExecutionDetails(taskId);
        checkBelongsToDomain(domain, details, taskId);
        taskManager.cancel(taskId);
        return Responses.returnNoContent(response);
    }

    private Object respondStatus(Domain domain, TaskId taskId, Supplier<TaskExecutionDetails> supplier) {
        try {
            TaskExecutionDetails details = supplier.get();
            checkBelongsToDomain(domain, details, taskId);
            return ExecutionDetailsDto.from(additionalInformationConverter, details);
        } catch (TaskNotFoundException e) {
            throw taskNotFound(taskId);
        }
    }

    private TaskExecutionDetails getExecutionDetails(TaskId taskId) {
        try {
            return taskManager.getExecutionDetails(taskId);
        } catch (TaskNotFoundException e) {
            throw taskNotFound(taskId);
        }
    }

    private void checkBelongsToDomain(Domain domain, TaskExecutionDetails details, TaskId taskId) {
        boolean belongs = predicates.stream()
            .anyMatch(predicate -> predicate.belongsToDomain(domain, details));
        if (!belongs) {
            throw taskNotFound(taskId);
        }
    }

    private Domain extractAndValidateDomain(Request request) {
        Domain domain = Domain.of(request.params(DOMAIN_PARAM));
        try {
            if (!domainList.containsDomain(domain)) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message(String.format("Domain %s does not exist", domain.asString()))
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
        return domain;
    }

    private TaskId extractTaskId(Request request) {
        try {
            return TaskId.fromString(request.params(TASK_ID_PARAM));
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid task id")
                .cause(e)
                .haltError();
        }
    }

    private Duration extractTimeout(Request request) {
        try {
            Duration timeout = Optional.ofNullable(request.queryParams("timeout"))
                .filter(Predicate.not(String::isEmpty))
                .map(s -> DurationParser.parse(s, ChronoUnit.SECONDS))
                .orElse(MAXIMUM_AWAIT_TIMEOUT);
            if (timeout.compareTo(MAXIMUM_AWAIT_TIMEOUT) > 0) {
                throw new IllegalArgumentException("Timeout should not exceed 365 days");
            }
            return timeout;
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid timeout")
                .cause(e)
                .haltError();
        }
    }

    private TaskExecutionDetails awaitTask(TaskId taskId, Duration timeout) {
        try {
            return taskManager.await(taskId, timeout);
        } catch (TaskManager.ReachedTimeoutException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.REQUEST_TIMEOUT_408)
                .type(ErrorResponder.ErrorType.SERVER_ERROR)
                .message("The timeout has been reached")
                .haltError();
        } catch (TaskNotFoundException e) {
            throw taskNotFound(taskId);
        }
    }

    private RuntimeException taskNotFound(TaskId taskId) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorResponder.ErrorType.NOT_FOUND)
            .message(String.format("%s can not be found", taskId.getValue()))
            .haltError();
    }
}
