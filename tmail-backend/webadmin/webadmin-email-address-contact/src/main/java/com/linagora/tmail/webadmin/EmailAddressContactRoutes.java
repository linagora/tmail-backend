package com.linagora.tmail.webadmin;

import javax.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.JsonTransformer;

import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;

import reactor.core.publisher.Flux;
import spark.Request;
import spark.Route;
import spark.Service;

public class EmailAddressContactRoutes implements Routes {
    private static final String CONTACT_DOMAIN_PARAM = ":dom";
    private static final String CONTACT_ADDRESS_PARAM = ":address";
    private static final String BASE_PATH = Constants.SEPARATOR + "domains" + Constants.SEPARATOR + CONTACT_DOMAIN_PARAM + Constants.SEPARATOR + "contacts";

    private final EmailAddressContactSearchEngine emailAddressContactSearchEngine;
    private final DomainList domainList;
    private final JsonTransformer jsonTransformer;

    @Inject
    public EmailAddressContactRoutes(EmailAddressContactSearchEngine emailAddressContactSearchEngine,
                                     DomainList domainList, JsonTransformer jsonTransformer) {
        this.emailAddressContactSearchEngine = emailAddressContactSearchEngine;
        this.domainList = domainList;
        this.jsonTransformer = jsonTransformer;
    }


    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(BASE_PATH, getContactsByDomain(), jsonTransformer);
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
}
