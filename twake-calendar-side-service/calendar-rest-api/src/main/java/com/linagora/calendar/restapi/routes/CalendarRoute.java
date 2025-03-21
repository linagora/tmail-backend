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

import org.apache.james.jmap.Endpoint;
import org.apache.james.jmap.JMAPRoute;
import org.apache.james.jmap.JMAPRoutes;
import org.apache.james.jmap.http.Authenticator;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;

import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public abstract class CalendarRoute implements JMAPRoutes {

    abstract Endpoint endpoint();

    abstract Mono<Void> handleRequest(HttpServerRequest request, HttpServerResponse response, MailboxSession session);

    private final Authenticator authenticator;
    private final MetricFactory metricFactory;

    protected CalendarRoute(Authenticator authenticator, MetricFactory metricFactory) {
        this.authenticator = authenticator;
        this.metricFactory = metricFactory;
    }

    @Override
    public Stream<JMAPRoute> routes() {
        return Stream.of(
            JMAPRoute.builder()
                .endpoint(endpoint())
                .action((req, res) -> authenticator.authenticate(req)
                    .flatMap(session -> Mono.from(metricFactory.decoratePublisherWithTimerMetric(this.getClass().getSimpleName(), handleRequest(req, res, session)))))
                .corsHeaders());
    }
}
