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

package com.linagora.tmail.webadmin.jmap;

import static org.apache.james.webadmin.Constants.SEPARATOR;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.linagora.tmail.james.jmap.settings.JmapSettingsKey;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsValue;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;
import spark.Request;
import spark.Service;

public class JmapSettingsReportRoutes implements Routes {
    static final String BASE_PATH = SEPARATOR + "jmap" + SEPARATOR + "settings" + SEPARATOR + "reports";
    static final String DOMAIN_PARAM = "domain";

    private final JmapSettingsRepository jmapSettingsRepository;
    private final UsersRepository usersRepository;
    private final DomainList domainList;
    private final JsonTransformer jsonTransformer;

    @Inject
    public JmapSettingsReportRoutes(JmapSettingsRepository jmapSettingsRepository,
                                    UsersRepository usersRepository,
                                    DomainList domainList,
                                    JsonTransformer jsonTransformer) {
        this.jmapSettingsRepository = jmapSettingsRepository;
        this.usersRepository = usersRepository;
        this.domainList = domainList;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(BASE_PATH, (req, res) -> getReport(req), jsonTransformer);
    }

    private Map<String, Map<String, Long>> getReport(Request request) throws DomainListException {
        return listUsers(Optional.ofNullable(request.queryParams(DOMAIN_PARAM)))
            .flatMap(username -> Mono.from(jmapSettingsRepository.get(username))
                .flatMapIterable(settings -> CollectionConverters.<JmapSettingsKey, JmapSettingsValue>asJava(settings.settings()).entrySet()))
            .collect(Collectors.groupingBy(
                entry -> entry.getKey().asString(),
                Collectors.groupingBy(
                    entry -> entry.getValue().value(),
                    Collectors.counting())))
            .block();
    }

    private Flux<Username> listUsers(Optional<String> domainParam) throws DomainListException {
        if (domainParam.isPresent()) {
            Domain domain = parseDomain(domainParam.get());
            validateDomain(domain);
            return Flux.from(usersRepository.listUsersOfADomainReactive(domain));
        }
        return Flux.from(usersRepository.listReactive());
    }

    private Domain parseDomain(String domainString) {
        try {
            return Domain.of(domainString);
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid domain: %s", domainString)
                .haltError();
        }
    }

    private void validateDomain(Domain domain) throws DomainListException {
        if (!domainList.containsDomain(domain)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message("Domain %s does not exist", domain.asString())
                .haltError();
        }
    }
}
