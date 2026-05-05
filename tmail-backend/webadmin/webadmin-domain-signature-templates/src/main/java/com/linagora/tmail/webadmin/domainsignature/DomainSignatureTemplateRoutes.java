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
 *******************************************************************/

package com.linagora.tmail.webadmin.domainsignature;

import static org.apache.james.webadmin.Constants.SEPARATOR;
import static spark.Spark.halt;

import java.util.Optional;

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

import com.linagora.tmail.james.jmap.domainsignature.DomainSignatureTemplateApplyService;
import com.linagora.tmail.james.jmap.domainsignature.DomainTemplateNotFoundException;
import com.linagora.tmail.james.jmap.event.DomainSignatureTemplateRepository;

import spark.Request;
import spark.Response;
import spark.Service;

public class DomainSignatureTemplateRoutes implements Routes {

    static final String DOMAINS_BASE = SEPARATOR + "domains";
    private static final String DOMAIN_PARAM = ":domain";
    static final String SIGNATURE_TEMPLATES_PATH =
        DOMAINS_BASE + SEPARATOR + DOMAIN_PARAM + SEPARATOR + "signature-templates";
    private static final String ACTION_PARAM = "action";
    private static final String APPLY_ACTION = "apply";

    private final DomainSignatureTemplateRepository repository;
    private final DomainList domainList;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<DomainSignatureTemplateDTO> jsonExtractor;
    private final Optional<DomainSignatureTemplateApplyService> applyService;

    @Inject
    public DomainSignatureTemplateRoutes(DomainSignatureTemplateRepository repository,
                                         DomainList domainList,
                                         JsonTransformer jsonTransformer,
                                         Optional<DomainSignatureTemplateApplyService> applyService) {
        this.repository = repository;
        this.domainList = domainList;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(DomainSignatureTemplateDTO.class);
        this.applyService = applyService;
    }

    @Override
    public String getBasePath() {
        return DOMAINS_BASE;
    }

    @Override
    public void define(Service service) {
        service.get(SIGNATURE_TEMPLATES_PATH, (req, res) -> getTemplate(req), jsonTransformer);
        service.put(SIGNATURE_TEMPLATES_PATH, (req, res) -> {
            putTemplate(req);
            return halt(HttpStatus.NO_CONTENT_204);
        });
        service.delete(SIGNATURE_TEMPLATES_PATH, (req, res) -> {
            deleteTemplate(req);
            return halt(HttpStatus.NO_CONTENT_204);
        });
        service.post(SIGNATURE_TEMPLATES_PATH, this::handlePost, jsonTransformer);
    }

    private Object handlePost(Request req, Response res) throws DomainListException {
        String action = req.queryParams(ACTION_PARAM);
        if (APPLY_ACTION.equals(action)) {
            return applyTemplate(req, res);
        }
        throw ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
            .message("Unknown action: '%s'", action)
            .haltError();
    }

    private Object applyTemplate(Request req, Response res) throws DomainListException {
        if (applyService.isEmpty()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_IMPLEMENTED_501)
                .type(ErrorResponder.ErrorType.SERVER_ERROR)
                .message("Domain signature template apply service is not available in this deployment")
                .haltError();
        }
        Domain domain = extractDomain(req);
        assertDomainExists(domain);
        try {
            return ApplyResultDTO.from(applyService.get().apply(domain).block());
        } catch (DomainTemplateNotFoundException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message(e.getMessage())
                .haltError();
        }
    }

    private Object getTemplate(Request request) throws DomainListException {
        Domain domain = extractDomain(request);
        assertDomainExists(domain);

        return repository.get(domain)
            .map(opt -> opt.map(DomainSignatureTemplateDTO::from)
                .orElseThrow(() -> ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("No signature template found for domain '%s'", domain.asString())
                    .haltError()))
            .block();
    }

    private void putTemplate(Request request) throws DomainListException, JsonExtractException {
        Domain domain = extractDomain(request);
        assertDomainExists(domain);

        DomainSignatureTemplateDTO dto = jsonExtractor.parse(request.body());
        if (dto.signatures() == null || dto.signatures().isEmpty()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("'signatures' must not be empty")
                .haltError();
        }
        repository.store(domain, dto.toDomain()).block();
    }

    private void deleteTemplate(Request request) throws DomainListException {
        Domain domain = extractDomain(request);
        assertDomainExists(domain);
        repository.delete(domain).block();
    }

    private Domain extractDomain(Request request) {
        try {
            return Domain.of(request.params(DOMAIN_PARAM));
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid domain: '%s'", request.params(DOMAIN_PARAM))
                .haltError();
        }
    }

    private void assertDomainExists(Domain domain) throws DomainListException {
        if (!domainList.containsDomain(domain)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message("Domain '%s' does not exist", domain.asString())
                .haltError();
        }
    }
}
