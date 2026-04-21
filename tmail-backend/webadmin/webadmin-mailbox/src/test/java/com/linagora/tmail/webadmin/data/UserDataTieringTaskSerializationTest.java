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

package com.linagora.tmail.webadmin.data;

import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.tiering.UserDataTieringService;

class UserDataTieringTaskSerializationTest {

    private static final Username BOB = Username.of("bob@domain.tld");
    private static final Duration TIERING = Duration.ofDays(30);

    private final UserDataTieringService tieringService = mock(UserDataTieringService.class);

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(UserDataTieringTaskDTO.module(tieringService))
            .bean(new UserDataTieringTask(tieringService, BOB, TIERING))
            .json("""
                {
                  "type": "UserDataTieringTask",
                  "username": "bob@domain.tld",
                  "tieringSeconds": 2592000
                }""")
            .verify();
    }
}
