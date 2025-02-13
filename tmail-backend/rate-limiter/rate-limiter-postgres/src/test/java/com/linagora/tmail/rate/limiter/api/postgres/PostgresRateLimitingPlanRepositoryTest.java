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

import org.apache.james.backends.postgres.PostgresExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.rate.limiter.api.RateLimitingPlanRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanRepositoryContract;
import com.linagora.tmail.rate.limiter.api.postgres.dao.PostgresRateLimitingPlanDAO;
import com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule;

public class PostgresRateLimitingPlanRepositoryTest implements RateLimitingPlanRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresRateLimitPlanModule.MODULE);

    @Override
    public RateLimitingPlanRepository testee() {
        return new PostgresRateLimitingPlanRepository(new PostgresRateLimitingPlanDAO(postgresExtension.getDefaultPostgresExecutor()));
    }
}
