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

package com.linagora.tmail.migration.webadmin;

import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.DisconnectorNotifier;
import org.apache.james.DisconnectorNotifier.MultipleUserRequest;
import org.apache.james.core.Username;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.linagora.tmail.migration.core.MigratedUsersRepository;

import spark.Request;
import spark.Response;
import spark.Route;
import spark.Service;

/**
 * Manages the list of migrated users (those routed to the new backend).
 *
 * <ul>
 *     <li>{@code PUT /migratedUsers/{username}} marks a user as migrated</li>
 *     <li>{@code DELETE /migratedUsers/{username}} removes a user from the migrated list</li>
 *     <li>{@code GET /migratedUsers} lists the migrated users</li>
 *     <li>{@code HEAD /migratedUsers/{username}} returns 204 when the user is migrated, 404 otherwise</li>
 * </ul>
 */
public class MigratedUsersRoutes implements Routes {
    private static final String USERNAME_PARAM = ":username";
    private static final String BASE_PATH = Constants.SEPARATOR + "migratedUsers";
    private static final String USER_PATH = BASE_PATH + Constants.SEPARATOR + USERNAME_PARAM;

    private final MigratedUsersRepository migratedUsersRepository;
    private final DisconnectorNotifier disconnectorNotifier;
    private final JsonTransformer jsonTransformer;

    @Inject
    public MigratedUsersRoutes(MigratedUsersRepository migratedUsersRepository,
                               DisconnectorNotifier disconnectorNotifier, JsonTransformer jsonTransformer) {
        this.migratedUsersRepository = migratedUsersRepository;
        this.disconnectorNotifier = disconnectorNotifier;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(BASE_PATH, listMigratedUsers(), jsonTransformer);
        service.put(USER_PATH, addMigratedUser());
        service.delete(USER_PATH, removeMigratedUser());
        service.head(USER_PATH, isMigrated());
    }

    private Route listMigratedUsers() {
        return (request, response) -> migratedUsersRepository.listMigratedUsers()
            .map(Username::asString)
            .collectList()
            .block();
    }

    private Route addMigratedUser() {
        return (request, response) -> {
            Username username = extractUsername(request);
            migratedUsersRepository.addMigratedUser(username).block();
            // Force the user's live proxied sessions to reconnect so they land on the new backend
            // straight away rather than staying pinned to the old one until they disconnect. The
            // request goes through the event bus so that, in a cluster of migration proxies, the node
            // actually holding the connection closes it, wherever the migration was triggered.
            disconnectorNotifier.disconnect(MultipleUserRequest.of(Set.of(username)));
            return noContent(response);
        };
    }

    private Route removeMigratedUser() {
        return (request, response) -> {
            migratedUsersRepository.removeMigratedUser(extractUsername(request)).block();
            return noContent(response);
        };
    }

    private Route isMigrated() {
        return (request, response) -> {
            boolean migrated = migratedUsersRepository.isMigrated(extractUsername(request))
                .blockOptional()
                .orElse(false);
            if (!migrated) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("User is not migrated")
                    .haltError();
            }
            return noContent(response);
        };
    }

    private Username extractUsername(Request request) {
        try {
            return Username.of(request.params(USERNAME_PARAM));
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid username")
                .cause(e)
                .haltError();
        }
    }

    private String noContent(Response response) {
        response.status(HttpStatus.NO_CONTENT_204);
        return Constants.EMPTY_BODY;
    }
}
