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

package com.linagora.tmail.domainlist.postgres;

import static com.linagora.tmail.domainlist.postgres.TMailPostgresDomainDataDefinition.PostgresDomainTable.ACTIVATED;
import static org.apache.james.backends.postgres.utils.PostgresExecutor.DEFAULT_INJECT;
import static org.apache.james.domainlist.postgres.PostgresDomainDataDefinition.PostgresDomainTable.DOMAIN;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.AbstractDomainList;
import org.apache.james.domainlist.postgres.PostgresDomainDataDefinition;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TmailPostgresDomainList extends AbstractDomainList {
    private static final boolean IS_ACTIVATED = true;

    private final PostgresExecutor postgresExecutor;

    @Inject
    public TmailPostgresDomainList(DNSService dnsService, @Named(DEFAULT_INJECT) PostgresExecutor postgresExecutor) {
        super(dnsService);
        this.postgresExecutor = postgresExecutor;
    }

    @Override
    public void addDomain(Domain domain) {
        postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(PostgresDomainDataDefinition.PostgresDomainTable.TABLE_NAME)
                .set(DOMAIN, domain.asString())
                .set(ACTIVATED, IS_ACTIVATED)
                .onConflict(DOMAIN)
                .doUpdate()
                .set(ACTIVATED, IS_ACTIVATED)))
            .block();
    }

    @Override
    protected List<Domain> getDomainListInternal() {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectFrom(PostgresDomainDataDefinition.PostgresDomainTable.TABLE_NAME)
                .where(ACTIVATED.eq(IS_ACTIVATED))))
            .map(record -> Domain.of(record.get(DOMAIN)))
            .collectList()
            .block();
    }

    @Override
    protected boolean containsDomainInternal(Domain domain) {
        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.selectFrom(PostgresDomainDataDefinition.PostgresDomainTable.TABLE_NAME)
                .where(DOMAIN.eq(domain.asString()))
                .and(ACTIVATED.eq(IS_ACTIVATED))))
            .blockOptional()
            .isPresent();
    }

    @Override
    protected void doRemoveDomain(Domain domain) throws DomainListException {
        boolean executed = postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.deleteFrom(PostgresDomainDataDefinition.PostgresDomainTable.TABLE_NAME)
                .where(DOMAIN.eq(domain.asString()))
                .returning(DOMAIN)))
            .blockOptional()
            .isPresent();

        if (!executed) {
            throw new DomainListException(domain.name() + " was not found");
        }
    }
}
