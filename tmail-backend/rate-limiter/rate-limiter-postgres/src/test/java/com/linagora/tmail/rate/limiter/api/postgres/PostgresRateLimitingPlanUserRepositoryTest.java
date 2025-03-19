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

package com.linagora.tmail.rate.limiter.api.postgres;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepositoryContract;
import com.linagora.tmail.rate.limiter.api.postgres.repository.PostgresRateLimitingPlanUserRepository;
import com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanUserTable;

class PostgresRateLimitingPlanUserRepositoryTest implements RateLimitingPlanUserRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(
        PostgresDataDefinition.aggregateModules(PostgresRateLimitPlanUserTable.MODULE()));

    private PostgresRateLimitingPlanUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PostgresRateLimitingPlanUserRepository(postgresExtension.getExecutorFactory(), postgresExtension.getByPassRLSPostgresExecutor());
    }

    @Override
    public RateLimitingPlanUserRepository testee() {
        return repository;
    }
}
