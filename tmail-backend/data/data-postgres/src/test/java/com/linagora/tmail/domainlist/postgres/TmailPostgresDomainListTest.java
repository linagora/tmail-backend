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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.domainlist.postgres;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.lib.DomainListContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

public class TmailPostgresDomainListTest implements DomainListContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(TMailPostgresDomainDataDefinition.MODULE);

    TmailPostgresDomainList domainList;

    @BeforeEach
    public void setup() throws Exception {
        domainList = new TmailPostgresDomainList(getDNSServer("localhost"), postgresExtension.getDefaultPostgresExecutor());
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
}
