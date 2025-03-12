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

import com.linagora.calendar.restapi.RestApiConfiguration;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

public class LogoRoute implements JMAPRoutes {
    private final RestApiConfiguration configuration;

    @Inject
    public LogoRoute(RestApiConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Stream<JMAPRoute> routes() {
        return Stream.of(
            JMAPRoute.builder()
                .endpoint(new Endpoint(HttpMethod.GET, "/api/themes/{domainId}/logo"))
                .action((req, res) -> res.status(HttpResponseStatus.MOVED_PERMANENTLY)
                    .header(HttpHeaderNames.LOCATION, configuration.getCalendarSpaUrl().toString() + "/assets/images/white-logo.svg")
                    .header("Cache-Control", "public, max-age=86400")
                    .send())
                .corsHeaders());
    }
}
