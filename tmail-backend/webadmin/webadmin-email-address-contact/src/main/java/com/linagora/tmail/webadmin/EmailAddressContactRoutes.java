package com.linagora.tmail.webadmin;

import static org.eclipse.jetty.http.HttpHeader.LOCATION;

import javax.inject.Inject;
import javax.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.webadmin.model.EmailAddressContactDTO;
import com.linagora.tmail.webadmin.model.EmailAddressContactIdResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spark.Request;
import spark.Route;
import spark.Service;

public class EmailAddressContactRoutes implements Routes {
    private static final String CONTACT_DOMAIN_PARAM = ":dom";
    private static final String CONTACT_ADDRESS_PARAM = ":address";

    private static final String ALL_DOMAINS_PATH = Constants.SEPARATOR + "domains" + Constants.SEPARATOR + "contacts";
    private static final String BASE_PATH = Constants.SEPARATOR + "domains" + Constants.SEPARATOR + CONTACT_DOMAIN_PARAM + Constants.SEPARATOR + "contacts";

    private final EmailAddressContactSearchEngine emailAddressContactSearchEngine;
    private final DomainList domainList;
    private final JsonTransformer jsonTransformer;

    private final JsonExtractor<EmailAddressContactDTO> jsonExtractor;

    @Inject
    public EmailAddressContactRoutes(EmailAddressContactSearchEngine emailAddressContactSearchEngine,
                                     DomainList domainList, JsonTransformer jsonTransformer) {
        this.emailAddressContactSearchEngine = emailAddressContactSearchEngine;
        this.domainList = domainList;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(EmailAddressContactDTO.class);
    }


    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(BASE_PATH, getContactsByDomain(), jsonTransformer);
        service.get(ALL_DOMAINS_PATH, getContacts(), jsonTransformer);
        service.post(BASE_PATH, createContact(), jsonTransformer);
    }

    private Domain extractDomain(Request request) {
        return Domain.of(request.params(CONTACT_DOMAIN_PARAM));
    }

    public Route getContactsByDomain() {
        return (request, response) -> {
            Domain domain = extractDomain(request);
            return Flux.from(emailAddressContactSearchEngine.list(domain))
                .map(contact -> contact.fields().address().asString())
                .collectList()
                .block();
        };
    }

    public Route getContacts() {
        return (request, response) ->
            Flux.from(emailAddressContactSearchEngine.listDomainsContacts())
                .map(contact -> contact.fields().address().asString())
                .collectList()
                .block();
    }

    public Route createContact() {
        return ((request, response) -> {
            Domain domain = extractDomain(request);
            verifyDomain(domain);

            try {
                EmailAddressContactDTO emailAddressContactDTO = jsonExtractor.parse(request.body());
                ContactFields contactFields = new ContactFields(
                    new MailAddress(emailAddressContactDTO.getEmailAddress()),
                    emailAddressContactDTO.getFirstname().orElse(""),
                    emailAddressContactDTO.getSurname().orElse(""));

                verifyMailAddressDomain(domain, contactFields.address());

                return Mono.from(emailAddressContactSearchEngine.index(domain, contactFields))
                    .map(contact -> {
                        response.status(HttpStatus.CREATED_201);
                        response.header(LOCATION.asString(), BASE_PATH.replace(CONTACT_DOMAIN_PARAM, domain.asString()) + Constants.SEPARATOR + contact.fields().address().getLocalPart());
                        return EmailAddressContactIdResponse.from(contact.id());
                    })
                    .block();
            } catch (AddressException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                    .message(e.getMessage())
                    .haltError();
            }
        });
    }

    private void verifyDomain(Domain domain) throws DomainListException {
        if (!domainList.containsDomain(domain)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message("The domain does not exist: " + domain.asString())
                .haltError();
        }
    }

    private void verifyMailAddressDomain(Domain domain, MailAddress mailAddress) {
        if (!mailAddress.getDomain().equals(domain)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("The domain " + domain.asString() + " does not match the one in the mail address: " + mailAddress.getDomain().asString())
                .haltError();
        }
    }
}
