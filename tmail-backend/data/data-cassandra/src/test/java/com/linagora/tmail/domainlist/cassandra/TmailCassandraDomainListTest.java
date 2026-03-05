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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.lib.DomainListContract;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;
import com.github.fge.lambdas.Throwing;

class TmailCassandraDomainListTest implements DomainListContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(TMailCassandraDomainListDataDefinition.MODULE);

    TmailCassandraDomainList domainList;

    @BeforeEach
    public void setUp(CassandraCluster cassandra) throws Exception {
        domainList = new TmailCassandraDomainList(getDNSServer("localhost"), cassandra.getConf());
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .build());
    }

    @Override
    public DomainList domainList() {
        return domainList;
    }

    @Override
    @Disabled("Upsert is authorized")
    public void addShouldBeCaseSensitive() {

    }

    @Override
    @Disabled("Upsert is authorized")
    public void addDomainShouldThrowIfWeAddTwoTimesTheSameDomain() {

    }

    @Test
    void emptyActivatedShouldNotReturnDomain(CassandraCluster cassandra) {
        CqlSession testingSession = cassandra.getConf();

        testingSession.execute(String.format("INSERT INTO domains (domain) VALUES ('%s')", DOMAIN_1.asString()));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Throwing.supplier(() -> domainList().containsDomain(DOMAIN_1)).get()).isFalse();
            softly.assertThat(Throwing.supplier(() -> domainList().getDomains()).get()).containsOnly(Domain.LOCALHOST /*default domain*/);
        });
    }

    @Test
    void activatedDomainShouldReturnDomain(CassandraCluster cassandra) {
        CqlSession testingSession = cassandra.getConf();
        boolean activated = true;

        testingSession.execute(String.format("INSERT INTO domains (domain, activated) VALUES ('%s', %b)", DOMAIN_1.asString(), activated));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Throwing.supplier(() -> domainList().containsDomain(DOMAIN_1)).get()).isTrue();
            softly.assertThat(Throwing.supplier(() -> domainList().getDomains()).get()).containsOnly(DOMAIN_1, Domain.LOCALHOST /*default domain*/);
        });
    }

    @Test
    void unactivatedDomainShouldNotReturnDomain(CassandraCluster cassandra) {
        CqlSession testingSession = cassandra.getConf();
        boolean activated = false;

        testingSession.execute(String.format("INSERT INTO domains (domain, activated) VALUES ('%s', %b)", DOMAIN_1.asString(), activated));

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Throwing.supplier(() -> domainList().containsDomain(DOMAIN_1)).get()).isFalse();
            softly.assertThat(Throwing.supplier(() -> domainList().getDomains()).get()).containsOnly(Domain.LOCALHOST /*default domain*/);
        });
    }

    @Test
    void addDomainShouldActivateExistingUnactivatedDomain(CassandraCluster cassandra) throws DomainListException {
        CqlSession testingSession = cassandra.getConf();
        boolean activated = false;

        testingSession.execute(String.format("INSERT INTO domains (domain, activated) VALUES ('%s', %b)", DOMAIN_1.asString(), activated));

        domainList().addDomain(DOMAIN_1);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Throwing.supplier(() -> domainList().containsDomain(DOMAIN_1)).get()).isTrue();
            softly.assertThat(Throwing.supplier(() -> domainList().getDomains()).get()).containsOnly(DOMAIN_1, Domain.LOCALHOST /*default domain*/);
        });
    }
}
