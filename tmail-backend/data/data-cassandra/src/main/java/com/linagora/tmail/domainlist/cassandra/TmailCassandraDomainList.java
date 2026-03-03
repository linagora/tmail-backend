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

package com.linagora.tmail.domainlist.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.linagora.tmail.domainlist.cassandra.TMailCassandraDomainListDataDefinition.ACTIVATED;
import static com.linagora.tmail.domainlist.cassandra.TMailCassandraDomainListDataDefinition.DOMAIN;
import static com.linagora.tmail.domainlist.cassandra.TMailCassandraDomainListDataDefinition.TABLE_NAME;

import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.ProfileLocator;
import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.AbstractDomainList;
import org.apache.james.util.ReactorUtils;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;

import reactor.core.publisher.Mono;

public class TmailCassandraDomainList extends AbstractDomainList {
    private static final boolean IS_ACTIVATED = true;

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement readAllStatement;
    private final PreparedStatement readStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement removeStatement;
    private final DriverExecutionProfile readProfile;
    private final DriverExecutionProfile writeProfile;

    @Inject
    public TmailCassandraDomainList(DNSService dnsService, CqlSession session) {
        super(dnsService);
        this.executor = new CassandraAsyncExecutor(session);
        this.readAllStatement = session.prepare(selectFrom(TABLE_NAME)
            .columns(DOMAIN, ACTIVATED)
            .build());

        this.readStatement = session.prepare(selectFrom(TABLE_NAME)
            .columns(DOMAIN, ACTIVATED)
            .whereColumn(DOMAIN).isEqualTo(bindMarker(DOMAIN))
            .build());

        this.insertStatement = session.prepare(insertInto(TABLE_NAME)
            .value(DOMAIN, bindMarker(DOMAIN))
            .value(ACTIVATED, bindMarker(ACTIVATED))
            .build());

        this.removeStatement = session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(DOMAIN).isEqualTo(bindMarker(DOMAIN))
            .ifExists()
            .build());
        this.readProfile = ProfileLocator.READ.locateProfile(session, "DOMAIN");
        this.writeProfile = ProfileLocator.WRITE.locateProfile(session, "DOMAIN");
    }

    @Override
    protected List<Domain> getDomainListInternal() {
        return executor.executeRows(readAllStatement.bind()
                .setExecutionProfile(readProfile))
            .filter(this::isDomainActivated)
            .map(row -> Domain.of(row.get(0, TypeCodecs.TEXT)))
            .collectList()
            .block();
    }

    @Override
    protected boolean containsDomainInternal(Domain domain) {
        return executor.executeSingleRowOptional(readStatement.bind()
                .set(DOMAIN, domain.asString(), TypeCodecs.TEXT)
                .setExecutionProfile(readProfile))
            .block()
            .filter(this::isDomainActivated)
            .isPresent();
    }

    @Override
    public Mono<Boolean> containsDomainReactive(Domain domain) {
        return executor.executeSingleRowOptional(readStatement.bind()
                .set(DOMAIN, domain.asString(), TypeCodecs.TEXT)
                .setExecutionProfile(readProfile))
            .handle(ReactorUtils.publishIfPresent())
            .filter(this::isDomainActivated)
            .hasElement();
    }

    @Override
    public void addDomain(Domain domain) throws DomainListException {
        executor.executeVoid(insertStatement.bind()
                .set(DOMAIN, domain.asString(), TypeCodecs.TEXT)
                .set(ACTIVATED, IS_ACTIVATED, TypeCodecs.BOOLEAN)
                .setExecutionProfile(writeProfile))
            .block();
    }

    @Override
    public void doRemoveDomain(Domain domain) throws DomainListException {
        boolean executed = executor.executeReturnApplied(removeStatement.bind()
                .set(DOMAIN, domain.asString(), TypeCodecs.TEXT)
                .setExecutionProfile(writeProfile))
            .block();
        if (!executed) {
            throw new DomainListException(domain.name() + " was not found");
        }
    }

    private boolean isDomainActivated(Row row) {
        return row.isNull(ACTIVATED)
            || Boolean.TRUE.equals(row.get(ACTIVATED, TypeCodecs.BOOLEAN));
    }
}
