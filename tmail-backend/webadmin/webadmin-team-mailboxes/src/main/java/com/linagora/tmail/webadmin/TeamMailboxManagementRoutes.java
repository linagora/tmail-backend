package com.linagora.tmail.webadmin;

import javax.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.base.Preconditions;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxName;
import com.linagora.tmail.team.TeamMailboxNotFoundException;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spark.HaltException;
import spark.Request;
import spark.Route;
import spark.Service;

public class TeamMailboxManagementRoutes implements Routes {
    public static class TeamMailboxResponse {
        static TeamMailboxResponse from(TeamMailbox teamMailbox) {
            Preconditions.checkNotNull(teamMailbox);
            return new TeamMailboxResponse(teamMailbox.mailboxName().asString(), teamMailbox.asMailAddress().asString());
        }

        static TeamMailboxResponse of(String name, String emailAddress) {
            Preconditions.checkNotNull(name);
            Preconditions.checkNotNull(emailAddress);
            return new TeamMailboxResponse(name, emailAddress);
        }

        private final String name;
        private final String emailAddress;

        public TeamMailboxResponse(String name, String emailAddress) {
            this.name = name;
            this.emailAddress = emailAddress;
        }

        public String getName() {
            return name;
        }

        public String getEmailAddress() {
            return emailAddress;
        }
    }

    static class TeamMailboxMemberResponse {
        private final Username username;

        TeamMailboxMemberResponse(Username username) {
            this.username = username;
        }

        public String getUsername() {
            return username.asString();
        }
    }

    private static final String TEAM_MAILBOX_DOMAIN_PARAM = ":dom";
    private static final String TEAM_MAILBOX_NAME_PARAM = ":name";
    private static final String MEMBER_USERNAME_PARAM = ":username";
    public static final String BASE_PATH = Constants.SEPARATOR + "domains" + Constants.SEPARATOR + TEAM_MAILBOX_DOMAIN_PARAM + Constants.SEPARATOR + "team-mailboxes";
    public static final String MEMBER_BASE_PATH = BASE_PATH + Constants.SEPARATOR + TEAM_MAILBOX_NAME_PARAM + Constants.SEPARATOR + "members";

    private final TeamMailboxRepository teamMailboxRepository;
    private final JsonTransformer jsonTransformer;

    @Inject
    public TeamMailboxManagementRoutes(TeamMailboxRepository teamMailboxRepository,
                                       JsonTransformer jsonTransformer) {
        this.teamMailboxRepository = teamMailboxRepository;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(BASE_PATH, getTeamMailboxesByDomain(), jsonTransformer);
        service.delete(BASE_PATH + Constants.SEPARATOR + TEAM_MAILBOX_NAME_PARAM, deleteTeamMailbox(), jsonTransformer);
        service.put(BASE_PATH + Constants.SEPARATOR + TEAM_MAILBOX_NAME_PARAM, addTeamMailbox(), jsonTransformer);

        service.get(MEMBER_BASE_PATH, getMembers(), jsonTransformer);
        service.delete(MEMBER_BASE_PATH + Constants.SEPARATOR + MEMBER_USERNAME_PARAM, deleteMember(), jsonTransformer);
        service.put(MEMBER_BASE_PATH + Constants.SEPARATOR + MEMBER_USERNAME_PARAM, addMember(), jsonTransformer);
    }

    private Domain extractDomain(Request request) {
        return Domain.of(request.params(TEAM_MAILBOX_DOMAIN_PARAM));
    }

    private TeamMailboxName extractName(Request request) {
        return TeamMailboxName.fromString(request.params(TEAM_MAILBOX_NAME_PARAM))
            .fold(e -> {
                throw e;
            }, x -> x);
    }

    private Username extractMemberUser(Request request) {
        return Username.of(request.params(MEMBER_USERNAME_PARAM));
    }

    private HaltException teamMailboxNotFoundException(TeamMailbox teamMailbox, Exception exception) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorResponder.ErrorType.NOT_FOUND)
            .message("The requested team mailbox does not exists")
            .cause(exception)
            .haltError();
    }

    public Route getTeamMailboxesByDomain() {
        return (request, response) -> {
            Domain domain = extractDomain(request);
            return Flux.from(teamMailboxRepository.listTeamMailboxes(domain))
                .map(TeamMailboxResponse::from)
                .collectList().block();
        };
    }

    public Route deleteTeamMailbox() {
        return (request, response) -> {
            Domain domain = extractDomain(request);
            TeamMailboxName teamMailboxName = extractName(request);

            Mono.from(teamMailboxRepository.deleteTeamMailbox(new TeamMailbox(domain, teamMailboxName))).block();
            return Responses.returnNoContent(response);
        };
    }

    public Route addTeamMailbox() {
        return (request, response) -> {
            Domain domain = extractDomain(request);
            TeamMailboxName teamMailboxName = extractName(request);
            Mono.from(teamMailboxRepository.createTeamMailbox(new TeamMailbox(domain, teamMailboxName))).block();
            return Responses.returnNoContent(response);
        };
    }

    public Route getMembers() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));
            return Flux.from(teamMailboxRepository.listMembers(teamMailbox))
                .map(TeamMailboxMemberResponse::new)
                .onErrorMap(TeamMailboxNotFoundException.class, e -> teamMailboxNotFoundException(teamMailbox, e))
                .collectList()
                .block();
        };
    }

    public Route addMember() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));
            Username addUser = extractMemberUser(request);
            Mono.from(teamMailboxRepository.addMember(teamMailbox, addUser))
                .onErrorMap(TeamMailboxNotFoundException.class, e -> teamMailboxNotFoundException(teamMailbox, e))
                .block();
            return Responses.returnNoContent(response);
        };
    }

    public Route deleteMember() {
        return (request, response) -> {
            TeamMailbox teamMailbox = new TeamMailbox(extractDomain(request), extractName(request));
            Username removeUser = extractMemberUser(request);
            Mono.from(teamMailboxRepository.removeMember(teamMailbox, removeUser))
                .onErrorMap(TeamMailboxNotFoundException.class, e -> teamMailboxNotFoundException(teamMailbox, e))
                .block();
            return Responses.returnNoContent(response);
        };
    }

}
