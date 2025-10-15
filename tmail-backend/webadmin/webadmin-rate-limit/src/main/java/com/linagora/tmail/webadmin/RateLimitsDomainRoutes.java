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

package com.linagora.tmail.webadmin;

import static org.apache.james.webadmin.Constants.SEPARATOR;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static spark.Spark.halt;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition;
import com.linagora.tmail.webadmin.model.RateLimitsDTO;

import reactor.core.publisher.Mono;
import spark.Request;
import spark.Route;
import spark.Service;

public class RateLimitsDomainRoutes implements Routes {
    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitsDomainRoutes.class);
    private static final String DOMAIN_PARAM = ":domain";
    private static final String RATE_LIMITS_BASE_PATH = SEPARATOR + "ratelimits";
    private static final String DOMAINS_PATH = SEPARATOR + "domains";
    private static final String PUT_RATE_LIMITS_TO_DOMAIN_PATH = DOMAINS_PATH + SEPARATOR + DOMAIN_PARAM + RATE_LIMITS_BASE_PATH;
    private static final String GET_RATE_LIMITS_OF_DOMAIN_PATH = DOMAINS_PATH + SEPARATOR + DOMAIN_PARAM + RATE_LIMITS_BASE_PATH;

    private final RateLimitingRepository rateLimitingRepository;
    private final DomainList domainList;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<RateLimitsDTO> jsonExtractor;

    @Inject
    public RateLimitsDomainRoutes(RateLimitingRepository rateLimitingRepository, DomainList domainList, JsonTransformer jsonTransformer) {
        this.rateLimitingRepository = rateLimitingRepository;
        this.domainList = domainList;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(RateLimitsDTO.class);
    }

    @Override
    public String getBasePath() {
        return RATE_LIMITS_BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.put(PUT_RATE_LIMITS_TO_DOMAIN_PATH, applyRateLimitsToDomain(), jsonTransformer);
        service.get(GET_RATE_LIMITS_OF_DOMAIN_PATH, getRateLimitsOfDomain(), jsonTransformer);
    }

    private Route applyRateLimitsToDomain() {
        return (request, response) -> {
            Domain domain = extractDomain(request);
            domainPreconditions(domain);
            try {
                RateLimitsDTO rateLimitsDTO = jsonExtractor.parse(request.body());
                Mono.from(rateLimitingRepository.setRateLimiting(domain, rateLimitsDTO.toRateLimitingDefinition())).block();
                return halt(HttpStatus.NO_CONTENT_204);
            } catch (JsonExtractException e) {
                LOGGER.info("Error while deserializing applyRateLimitsToDomain request", e);
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                    .message("Error while deserializing applyRateLimitsToDomain request")
                    .cause(e)
                    .haltError();
            }
        };
    }

    private Route getRateLimitsOfDomain() {
        return (request, response) -> {
            Domain domain = extractDomain(request);
            domainPreconditions(domain);
            return Mono.from(rateLimitingRepository.getRateLimiting(domain))
                .map(this::toRateLimitsDTO)
                .block();
        };
    }

    private Domain extractDomain(Request request) {
        return Domain.of(request.params(DOMAIN_PARAM));
    }

    private void domainPreconditions(Domain domain) throws DomainListException {
        if (!domainList.containsDomain(domain)) {
            throw ErrorResponder.builder()
                .statusCode(NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message(String.format("Domain %s does not exist", domain.asString()))
                .haltError();
        }
    }

    private RateLimitsDTO toRateLimitsDTO(RateLimitingDefinition rateLimitingDefinition) {
        return new RateLimitsDTO(rateLimitingDefinition.mailsSentPerMinute(),
            rateLimitingDefinition.mailsSentPerHours(),
            rateLimitingDefinition.mailsSentPerDays(),
            rateLimitingDefinition.mailsReceivedPerMinute(),
            rateLimitingDefinition.mailsReceivedPerHours(),
            rateLimitingDefinition.mailsReceivedPerDays());
    }
}
