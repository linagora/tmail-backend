package com.linagora.tmail.webadmin;

import java.util.Objects;

import javax.inject.Inject;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.Domain;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepository;

import io.swagger.annotations.Api;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;
import spark.Request;
import spark.Service;

@Api(tags = "TeamMailbox Management")
@Path("/domains/{dom}/team-mailboxes")
@Produces("application/json")
public class TeamMailboxManagementRoutes implements Routes {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeamMailboxManagementRoutes.class);
    static class TeamMailboxResponse {
        static TeamMailboxResponse from(TeamMailbox teamMailbox) {
            Preconditions.checkNotNull(teamMailbox);
            String mailboxName = teamMailbox.mailboxName().valueAsJava();
            return new TeamMailboxResponse(mailboxName, mailboxName + "@" + teamMailbox.domain().asString());
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
    private Service service;

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
        this.service = service;
        defineGetTeamMailboxesByDomain();
        defineAddTeamMailbox();
        defineDeleteTeamMailbox();
    }

    private Domain extractTMDomain(Request request) {
        return Domain.of(request.params(TEAM_MAILBOX_DOMAIN_PARAM));
    }

    // todo validate
    private String extractTMName(Request request) {
        return request.params(TEAM_MAILBOX_NAME_PARAM);
    }

    public void defineGetTeamMailboxesByDomain() {
        service.get(BASE_PATH, (request, response) -> {
            Domain tmDomain = extractTMDomain(request);
            return Objects.requireNonNull(Flux.from(teamMailboxRepository.listTeamMailboxes(tmDomain)).collectList().block())
                .stream()
                .map(TeamMailboxResponse::from)
                .collect(ImmutableList.toImmutableList());
        }, jsonTransformer);
    }

    public void defineDeleteTeamMailbox() {
        service.delete(BASE_PATH + Constants.SEPARATOR + TEAM_MAILBOX_NAME_PARAM, (request, response) -> {
            Domain tmDomain = extractTMDomain(request);
            String tmName = extractTMName(request);

            OptionConverters.toJava(TeamMailbox.fromJava(tmDomain, tmName))
                .ifPresentOrElse(teamMailbox -> Mono.from(teamMailboxRepository.deleteTeamMailbox(teamMailbox)).block(),
                    () -> {
                        throw new IllegalArgumentException("Team mailbox name invalid");
                    }
                );
            return Responses.returnNoContent(response);
        }, jsonTransformer);
    }

    public void defineAddTeamMailbox() {
        service.put(BASE_PATH + Constants.SEPARATOR + TEAM_MAILBOX_NAME_PARAM, (request, response) -> {
            Domain tmDomain = extractTMDomain(request);
            String tmName = extractTMName(request);

            OptionConverters.toJava(TeamMailbox.fromJava(tmDomain, tmName))
                .ifPresentOrElse(teamMailbox -> Mono.from(teamMailboxRepository.createTeamMailbox(teamMailbox)).block(),
                    () -> {
                        throw new IllegalArgumentException("Team mailbox name invalid");
                    }
                );
            return Responses.returnNoContent(response);
        }, jsonTransformer);
    }
}
