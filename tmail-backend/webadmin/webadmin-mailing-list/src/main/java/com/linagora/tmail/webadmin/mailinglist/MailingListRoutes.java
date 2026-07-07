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

package com.linagora.tmail.webadmin.mailinglist;

import static org.apache.james.webadmin.Constants.SEPARATOR;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.collect.ImmutableList;

import spark.Request;
import spark.Service;

public class MailingListRoutes implements Routes {
    public static final String BASE_PATH = SEPARATOR + "mailingLists";
    private static final String ADDRESS_PARAM = ":address";
    private static final String SPECIFIC_LIST_PATH = BASE_PATH + SEPARATOR + ADDRESS_PARAM;
    private static final String DOMAIN_QUERY_PARAM = "domain";

    public record MailingListResponse(String mail, String businessCategory, List<String> members, List<String> owners) {
        public static MailingListResponse from(MailingList mailingList) {
            return new MailingListResponse(
                mailingList.address().asString(),
                mailingList.businessCategory().orElse(""),
                mailingList.members().stream().map(MailAddress::asString).collect(ImmutableList.toImmutableList()),
                mailingList.owners().stream().map(MailAddress::asString).collect(ImmutableList.toImmutableList()));
        }
    }

    private final MailingListRepository mailingListRepository;
    private final JsonTransformer jsonTransformer;

    @Inject
    public MailingListRoutes(MailingListRepository mailingListRepository, JsonTransformer jsonTransformer) {
        this.mailingListRepository = mailingListRepository;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(BASE_PATH, (req, res) -> listMailingLists(req), jsonTransformer);
        service.get(SPECIFIC_LIST_PATH, (req, res) -> getMailingList(req), jsonTransformer);
    }

    private List<String> listMailingLists(Request request) {
        try {
            List<MailAddress> addresses = Optional.ofNullable(request.queryParams(DOMAIN_QUERY_PARAM))
                .map(this::parseDomain)
                .map(mailingListRepository::list)
                .orElseGet(mailingListRepository::list);
            return addresses.stream()
                .map(MailAddress::asString)
                .collect(ImmutableList.toImmutableList());
        } catch (MailingListsNotConfiguredException e) {
            throw notConfigured(e);
        }
    }

    private MailingListResponse getMailingList(Request request) {
        MailAddress address = parseAddress(request.params(ADDRESS_PARAM));
        try {
            return mailingListRepository.get(address)
                .map(MailingListResponse::from)
                .orElseThrow(() -> ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("The mailing list '%s' does not exist", address.asString())
                    .haltError());
        } catch (MailingListsNotConfiguredException e) {
            throw notConfigured(e);
        }
    }

    private Domain parseDomain(String domain) {
        try {
            return Domain.of(domain);
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid domain '%s'", domain)
                .cause(e)
                .haltError();
        }
    }

    private MailAddress parseAddress(String address) {
        try {
            return new MailAddress(address);
        } catch (AddressException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid mail address '%s'", address)
                .cause(e)
                .haltError();
        }
    }

    private RuntimeException notConfigured(MailingListsNotConfiguredException e) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.CONFLICT_409)
            .type(ErrorResponder.ErrorType.WRONG_STATE)
            .message(e.getMessage())
            .cause(e)
            .haltError();
    }
}
