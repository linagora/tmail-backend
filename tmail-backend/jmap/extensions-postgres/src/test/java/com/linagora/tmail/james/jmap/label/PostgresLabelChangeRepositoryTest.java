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

package com.linagora.tmail.james.jmap.label;

import java.time.ZonedDateTime;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.postgres.change.PostgresStateFactory;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.jmap.firebase.FirebaseSubscriptionRepositoryContract;

class PostgresLabelChangeRepositoryTest implements LabelChangeRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(
        PostgresModule.aggregateModules(PostgresLabelModule.MODULE));

    private PostgresLabelChangeRepository labelChangeRepository;

    @BeforeEach
    void setup() {
        UpdatableTickingClock clock = new UpdatableTickingClock(FirebaseSubscriptionRepositoryContract.NOW());
        labelChangeRepository = new PostgresLabelChangeRepository(postgresExtension.getExecutorFactory(), clock);
    }

    @Override
    public LabelChangeRepository testee() {
        return labelChangeRepository;
    }

    @Override
    public State.Factory stateFactory() {
        return new PostgresStateFactory();
    }

    @Override
    public void setClock(ZonedDateTime newTime) {

    }
}
