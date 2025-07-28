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

package com.linagora.tmail.saas.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.saas.model.SaaSPlan;

import reactor.core.publisher.Mono;

public interface SaaSUserRepositoryContract {
    SaaSUserRepository testee();

    Username BOB = Username.of("bob@domain.tld");
    Username ALICE = Username.of("alice@domain.tld");

    @Test
    default void setPlanShouldSucceed() {
        Mono.from(testee().setPlan(BOB, SaaSPlan.FREE)).block();

        assertThat(Mono.from(testee().getPlan(BOB)).block())
            .isEqualTo(SaaSPlan.FREE);
    }

    @Test
    default void setPlanShouldOverridePreviousPlan() {
        Mono.from(testee().setPlan(BOB, SaaSPlan.FREE)).block();
        Mono.from(testee().setPlan(BOB, SaaSPlan.PREMIUM)).block();

        assertThat(Mono.from(testee().getPlan(BOB)).block())
            .isEqualTo(SaaSPlan.PREMIUM);
    }

    @Test
    default void getPlanShouldReturnFreeByDefault() {
        assertThat(Mono.from(testee().getPlan(ALICE)).block())
            .isEqualTo(SaaSPlan.FREE);
    }
}
