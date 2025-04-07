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

package com.linagora.calendar.restapi.routes;

import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.james.jmap.Endpoint;
import org.apache.james.jmap.JMAPRoute;
import org.apache.james.jmap.JMAPRoutes;
import org.apache.james.metrics.api.MetricFactory;

import com.linagora.calendar.restapi.NotFoundException;
import com.linagora.calendar.restapi.RestApiConfiguration;
import com.linagora.calendar.storage.OpenPaaSId;
import com.linagora.calendar.storage.OpenPaaSUserDAO;

import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class ProfileAvatarRoute implements JMAPRoutes {
    private final MetricFactory metricFactory;
    private final OpenPaaSUserDAO userDAO;
    private final RestApiConfiguration configuration;

    @Inject
    public ProfileAvatarRoute(MetricFactory metricFactory, OpenPaaSUserDAO userDAO, RestApiConfiguration configuration) {
        this.metricFactory = metricFactory;
        this.userDAO = userDAO;
        this.configuration = configuration;
    }

    Endpoint endpoint() {
        return new Endpoint(HttpMethod.GET, "/api/users/{userId}/profile/avatar");
    }

    @Override
    public Stream<JMAPRoute> routes() {
        return Stream.of(
            JMAPRoute.builder()
                .endpoint(endpoint())
                .action((req, res) -> Mono.from(metricFactory.decoratePublisherWithTimerMetric(this.getClass().getSimpleName(), generateAvatar(req, res))))
                .corsHeaders());
    }

    Mono<Void> generateAvatar(HttpServerRequest request, HttpServerResponse response) {
        return userDAO.retrieve(new OpenPaaSId(request.param("userId")))
            .switchIfEmpty(Mono.error(NotFoundException::new))
            .flatMap(user -> response
                .status(302)
                .header("Location", configuration.getSelfUrl() + "/api/avatars?email=" + user.username().asString())
                .header("Cache-Control", "max-age=1800, public")
                .send());
    }
}