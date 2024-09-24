package com.linagora.tmail.webadmin;

import static org.eclipse.jetty.http.HttpHeader.LOCATION;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.ContactNotFoundException;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.webadmin.model.ContactNameUpdateDTO;
import com.linagora.tmail.webadmin.model.EmailAddressContactDTO;
import com.linagora.tmail.webadmin.model.EmailAddressContactIdResponse;
import com.linagora.tmail.webadmin.model.EmailAddressContactResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spark.Request;
import spark.Route;
import spark.Service;

public class EmailAddressContactRoutes implements Routes {
    private static final String CONTACT_DOMAIN_PARAM = ":dom";
    private static final String CONTACT_ADDRESS_PARAM = ":address";

    private static final String ALL_DOMAINS_PATH = Constants.SEPARATOR + "domains" + Constants.SEPARATOR + "contacts" + Constants.SEPARATOR + "all";
    private static final String BASE_PATH = Constants.SEPARATOR + "domains" + Constants.SEPARATOR + CONTACT_DOMAIN_PARAM + Constants.SEPARATOR + "contacts";
    private static final String CRUD_PATH = BASE_PATH + Constants.SEPARATOR + CONTACT_ADDRESS_PARAM;

    private final EmailAddressContactSearchEngine emailAddressContactSearchEngine;
    private final DomainList domainList;
    private final JsonTransformer jsonTransformer;

    private final JsonExtractor<EmailAddressContactDTO> jsonExtractorContact;
    private final JsonExtractor<ContactNameUpdateDTO> jsonExtractorName;

    @Inject
    public EmailAddressContactRoutes(EmailAddressContactSearchEngine emailAddressContactSearchEngine,
                                     DomainList domainList, JsonTransformer jsonTransformer) {
        this.emailAddressContactSearchEngine = emailAddressContactSearchEngine;
        this.domainList = domainList;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractorContact = new JsonExtractor<>(EmailAddressContactDTO.class);
        this.jsonExtractorName = new JsonExtractor<>(ContactNameUpdateDTO.class);
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
        service.delete(CRUD_PATH, deleteContact(), jsonTransformer);
        service.put(CRUD_PATH, updateContact(), jsonTransformer);
        service.get(CRUD_PATH, getContactInfo(), jsonTransformer);
    }

    private Domain extractDomain(Request request) {
        return Domain.of(request.params(CONTACT_DOMAIN_PARAM));
    }

    private String extractAddressLocalPart(Request request) {
        return request.params(CONTACT_ADDRESS_PARAM);
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
                EmailAddressContactDTO emailAddressContactDTO = jsonExtractorContact.parse(request.body());
                ContactFields contactFields = new ContactFields(
                    new MailAddress(emailAddressContactDTO.emailAddress()),
                    emailAddressContactDTO.firstname().orElse(""),
                    emailAddressContactDTO.surname().orElse(""));

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

    public Route deleteContact() {
        return ((request, response) -> {
            Domain domain = extractDomain(request);

            try {
                MailAddress mailAddress = MailAddress.of(extractAddressLocalPart(request), domain);

                return Mono.from(emailAddressContactSearchEngine.delete(domain, mailAddress))
                    .then(Mono.just(Responses.returnNoContent(response)))
                    .block();
            } catch (AddressException e) {
                return throwAddressException();
            }
        });
    }

    public Route updateContact() {
        return ((request, response) -> {
            Domain domain = extractDomain(request);
            verifyDomain(domain);

            try {
                MailAddress mailAddress = MailAddress.of(extractAddressLocalPart(request), domain);
                ContactNameUpdateDTO contactNameUpdateDTO = jsonExtractorName.parse(request.body());

                return fillUpMissingNameFields(domain, mailAddress, contactNameUpdateDTO)
                    .flatMap(updatedContact -> Mono.from(emailAddressContactSearchEngine.update(domain, updatedContact)))
                    .then(Mono.just(Responses.returnNoContent(response)))
                    .block();
            } catch (AddressException e) {
                return throwAddressException();
            }
        });
    }

    private Mono<ContactFields> fillUpMissingNameFields(Domain domain, MailAddress mailAddress, ContactNameUpdateDTO updatedFields) {
        if (updatedFields.firstname().isEmpty() || updatedFields.surname().isEmpty()) {
            return Mono.from(emailAddressContactSearchEngine.get(domain, mailAddress))
                .map(contact -> fillUp(contact.fields(), updatedFields))
                .defaultIfEmpty(fromDTOToContactFields(mailAddress, updatedFields));
        }
        return Mono.just(fromDTOToContactFields(mailAddress, updatedFields));
    }

    private ContactFields fillUp(ContactFields presentFields, ContactNameUpdateDTO updatedFields) {
        return new ContactFields(
            presentFields.address(),
            updatedFields.firstname().orElse(presentFields.firstname()),
            updatedFields.surname().orElse(presentFields.surname()));
    }

    private ContactFields fromDTOToContactFields(MailAddress mailAddress, ContactNameUpdateDTO contactNameUpdateDTO) {
        return new ContactFields(
            mailAddress,
            contactNameUpdateDTO.firstname().orElse(""),
            contactNameUpdateDTO.surname().orElse(""));
    }

    public Route getContactInfo() {
        return ((request, response) -> {
            Domain domain = extractDomain(request);

            try {
                MailAddress mailAddress = MailAddress.of(extractAddressLocalPart(request), domain);

                return Mono.from(emailAddressContactSearchEngine.get(domain, mailAddress))
                    .map(EmailAddressContactResponse::from)
                    .onErrorResume(ContactNotFoundException.class, e -> {
                        throw ErrorResponder.builder()
                            .statusCode(HttpStatus.NOT_FOUND_404)
                            .type(ErrorResponder.ErrorType.NOT_FOUND)
                            .message(e.getMessage())
                            .haltError();
                    })
                    .block();
            } catch (AddressException e) {
                return throwAddressException();
            }
        });
    }

    private Object throwAddressException() {
        throw ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
            .message("Mail address is wrong. Be sure to include only the local part in the path")
            .haltError();
    }
}
