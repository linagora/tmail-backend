package com.linagora.tmail.webadmin;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spark.Request;
import spark.Route;
import spark.Service;

public class UserTeamMailboxRoutes implements Routes {
    private static final String USERNAME_PARAM = ":username";
    private static final String BASE_PATH = Constants.SEPARATOR + "users" + Constants.SEPARATOR + USERNAME_PARAM + Constants.SEPARATOR + "team-mailboxes";

    private final TeamMailboxRepository teamMailboxRepository;
    private final JsonTransformer jsonTransformer;
    private final UsersRepository usersRepository;

    @Inject
    public UserTeamMailboxRoutes(TeamMailboxRepository teamMailboxRepository,
                                 JsonTransformer jsonTransformer, UsersRepository usersRepository) {
        this.teamMailboxRepository = teamMailboxRepository;
        this.jsonTransformer = jsonTransformer;
        this.usersRepository = usersRepository;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(BASE_PATH, getTeamMailboxes(), jsonTransformer);
    }

    private Username extractUsername(Request request) {
        return Username.of(request.params(USERNAME_PARAM));
    }

    public void userPreconditions(Username username) {
        if (!Throwing.supplier(() -> usersRepository.contains(username)).sneakyThrow().get()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message(String.format("User %s does not exist", username.asString()))
                .haltError();
        }
    }

    public Route getTeamMailboxes() {
        return (request, response) -> Mono.just(extractUsername(request))
            .flatMap(username -> Mono.fromCallable(() -> {
                userPreconditions(username);
                return username;
            }))
            .flatMapMany(username -> Flux.from(teamMailboxRepository.listTeamMailboxes(username)))
            .map(TeamMailboxManagementRoutes.TeamMailboxResponse::from)
            .collectList()
            .block();
    }
}
