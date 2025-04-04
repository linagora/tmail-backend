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
import org.reactivestreams.Publisher;

public class OpenPaaSDomainList implements DomainList {
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

    public void removeDomain(Domain domain) {
        throw new RuntimeException("Not implemented exception");
    }

    public Domain getDefaultDomain() {
        throw new RuntimeException("Not implemented exception");
    }
}
