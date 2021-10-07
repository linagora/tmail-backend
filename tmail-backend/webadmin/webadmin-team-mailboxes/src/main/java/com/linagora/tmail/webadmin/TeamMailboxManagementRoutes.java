package com.linagora.tmail.webadmin;

import javax.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;

import com.google.common.base.Preconditions;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxName;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spark.Request;
import spark.Route;
import spark.Service;

public class TeamMailboxManagementRoutes implements Routes {
    static class TeamMailboxResponse {
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

    private static final String TEAM_MAILBOX_DOMAIN_PARAM = ":dom";
    private static final String TEAM_MAILBOX_NAME_PARAM = ":name";
    public static final String BASE_PATH = "/domains/" + TEAM_MAILBOX_DOMAIN_PARAM + "/team-mailboxes";

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
}
