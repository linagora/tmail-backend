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
import java.util.Set;
import java.util.concurrent.Callable;

import jakarta.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import spark.Request;
import spark.Response;
import spark.Service;

/**
 * <p>Write routes for managing LDAP mailing lists: create/delete a list and add/remove members and owners.</p>
 *
 * <p>Unlike the read only {@link MailingListRoutes}, this class is <b>not</b> registered in the {@code Multibinder<Routes>}.
 * It is only exposed when an operator lists its fully qualified class name under {@code additional.routes} in
 * {@code webadmin.properties}, which gives the opt-in the issue asks for while the read routes stay always on. It
 * requires the configured LDAP bind user to hold write ACLs on the lists subtree.</p>
 */
public class MailingListManagementRoutes implements Routes {
    private static final String ADDRESS_PARAM = ":address";
    private static final String MEMBER_PARAM = ":memberAddress";
    private static final String OWNER_PARAM = ":ownerAddress";
    private static final String SPECIFIC_LIST_PATH = MailingListRoutes.BASE_PATH + SEPARATOR + ADDRESS_PARAM;
    private static final String MEMBER_PATH = SPECIFIC_LIST_PATH + SEPARATOR + "members" + SEPARATOR + MEMBER_PARAM;
    private static final String OWNER_PATH = SPECIFIC_LIST_PATH + SEPARATOR + "owners" + SEPARATOR + OWNER_PARAM;
    private static final Set<String> VALID_BUSINESS_CATEGORIES = ImmutableSet.of(
        "openlist", "internallist", "memberrestrictedlist", "ownerrestrictedlist", "domainrestrictedlist");

    public record MailingListCreationRequest(String businessCategory, List<String> members, List<String> owners) {
    }

    private final MailingListRepository mailingListRepository;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<MailingListCreationRequest> jsonExtractor;

    @Inject
    public MailingListManagementRoutes(MailingListRepository mailingListRepository, JsonTransformer jsonTransformer) {
        this.mailingListRepository = mailingListRepository;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(MailingListCreationRequest.class);
    }

    @Override
    public String getBasePath() {
        return MailingListRoutes.BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.put(SPECIFIC_LIST_PATH, this::createMailingList, jsonTransformer);
        service.delete(SPECIFIC_LIST_PATH, this::deleteMailingList, jsonTransformer);
        service.put(MEMBER_PATH, this::addMember, jsonTransformer);
        service.delete(MEMBER_PATH, this::removeMember, jsonTransformer);
        service.put(OWNER_PATH, this::addOwner, jsonTransformer);
        service.delete(OWNER_PATH, this::removeOwner, jsonTransformer);
    }

    private String createMailingList(Request request, Response response) {
        MailAddress address = parseAddress(request.params(ADDRESS_PARAM));
        MailingListCreationRequest creationRequest = parseBody(request);
        Optional<String> businessCategory = Optional.ofNullable(creationRequest.businessCategory())
            .map(String::trim)
            .filter(value -> !value.isEmpty());
        businessCategory.ifPresent(this::validateBusinessCategory);
        List<MailAddress> members = parseAddresses(creationRequest.members());
        if (members.isEmpty()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("A mailing list must be created with at least one member")
                .haltError();
        }
        MailingList mailingList = new MailingList(address, businessCategory, members,
            parseAddresses(creationRequest.owners()));
        return execute(response, () -> mailingListRepository.create(mailingList));
    }

    private String deleteMailingList(Request request, Response response) {
        MailAddress address = parseAddress(request.params(ADDRESS_PARAM));
        return execute(response, () -> mailingListRepository.delete(address));
    }

    private String addMember(Request request, Response response) {
        MailAddress address = parseAddress(request.params(ADDRESS_PARAM));
        MailAddress member = parseAddress(request.params(MEMBER_PARAM));
        return execute(response, () -> mailingListRepository.addMember(address, member));
    }

    private String removeMember(Request request, Response response) {
        MailAddress address = parseAddress(request.params(ADDRESS_PARAM));
        MailAddress member = parseAddress(request.params(MEMBER_PARAM));
        return execute(response, () -> mailingListRepository.removeMember(address, member));
    }

    private String addOwner(Request request, Response response) {
        MailAddress address = parseAddress(request.params(ADDRESS_PARAM));
        MailAddress owner = parseAddress(request.params(OWNER_PARAM));
        return execute(response, () -> mailingListRepository.addOwner(address, owner));
    }

    private String removeOwner(Request request, Response response) {
        MailAddress address = parseAddress(request.params(ADDRESS_PARAM));
        MailAddress owner = parseAddress(request.params(OWNER_PARAM));
        return execute(response, () -> mailingListRepository.removeOwner(address, owner));
    }

    private String execute(Response response, Runnable action) {
        try {
            action.run();
            return Responses.returnNoContent(response);
        } catch (MailingListsNotConfiguredException e) {
            throw halt(HttpStatus.CONFLICT_409, ErrorType.WRONG_STATE, e);
        } catch (MailingListManagementException.AlreadyExists e) {
            throw halt(HttpStatus.CONFLICT_409, ErrorType.WRONG_STATE, e);
        } catch (MailingListManagementException.NotFound e) {
            throw halt(HttpStatus.NOT_FOUND_404, ErrorType.NOT_FOUND, e);
        } catch (MailingListManagementException.UnknownMember e) {
            throw halt(HttpStatus.BAD_REQUEST_400, ErrorType.INVALID_ARGUMENT, e);
        } catch (MailingListManagementException e) {
            throw halt(HttpStatus.CONFLICT_409, ErrorType.WRONG_STATE, e);
        }
    }

    private MailingListCreationRequest parseBody(Request request) {
        try {
            return jsonExtractor.parse(request.body());
        } catch (JsonExtractException e) {
            throw halt(HttpStatus.BAD_REQUEST_400, ErrorType.INVALID_ARGUMENT, e);
        }
    }

    private void validateBusinessCategory(String businessCategory) {
        if (!VALID_BUSINESS_CATEGORIES.contains(businessCategory.toLowerCase())) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Invalid businessCategory '%s'. Expected one of openList, internalList, "
                    + "memberRestrictedList, ownerRestrictedList, domainRestrictedList", businessCategory)
                .haltError();
        }
    }

    private List<MailAddress> parseAddresses(List<String> addresses) {
        return Optional.ofNullable(addresses)
            .orElseGet(List::of)
            .stream()
            .map(this::parseAddress)
            .collect(ImmutableList.toImmutableList());
    }

    private MailAddress parseAddress(String address) {
        return parseOrBadRequest(() -> new MailAddress(address), "Invalid mail address '%s'", address);
    }

    private <T> T parseOrBadRequest(Callable<T> parser, String errorMessage, String value) {
        try {
            return parser.call();
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message(errorMessage, value)
                .cause(e)
                .haltError();
        }
    }

    private RuntimeException halt(int statusCode, ErrorType errorType, Exception e) {
        return ErrorResponder.builder()
            .statusCode(statusCode)
            .type(errorType)
            .message(e.getMessage())
            .cause(e)
            .haltError();
    }
}
