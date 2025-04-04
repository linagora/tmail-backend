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

package com.linagora.calendar.storage;

import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.lifecycle.api.Startable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenPaaSDomainList implements DomainList, Startable {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaaSDomainList.class);

    private final OpenPaaSDomainDAO domainDAO;

    @Inject
    public OpenPaaSDomainList(OpenPaaSDomainDAO domainDAO) {
        this.domainDAO = domainDAO;
    }

    public List<Domain> getDomains() {
        return domainDAO.list()
            .map(domain -> domain.domain())
            .collectList()
            .block();
    }

    public boolean containsDomain(Domain domain) {
        return domainDAO.retrieve(domain)
            .blockOptional()
            .isPresent();
    }

    public Publisher<Boolean> containsDomainReactive(Domain domain) {
        return domainDAO.retrieve(domain)
            .hasElement();
    }

    public void addDomain(Domain domain) {
        domainDAO.add(domain).block();
    }

    public void addDomainLenient(Domain domain) {
        try {
            domainDAO.add(domain).block();
        } catch (IllegalStateException e) {
            LOGGER.info("Domain '{}' already exist", domain.asString());
        }
    }

    public void removeDomain(Domain domain) {
        throw new RuntimeException("Not implemented exception");
    }

    public Domain getDefaultDomain() {
        throw new RuntimeException("Not implemented exception");
    }
}
