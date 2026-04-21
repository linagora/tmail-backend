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

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.junit.jupiter.api.Test;

class UserDataTieringTaskAdditionalInformationDTOTest {

    @Test
    void beanTest() throws Exception {
        JsonSerializationVerifier.dtoModule(UserDataTieringTaskAdditionalInformationDTO.module())
            .bean(new UserDataTieringTask.AdditionalInformation(
                Instant.parse("2007-12-03T10:15:30.00Z"),
                "bob@domain.tld",
                2592000L,
                42L,
                3L))
            .json("""
                {
                  "type": "UserDataTieringTask",
                  "timestamp": "2007-12-03T10:15:30Z",
                  "username": "bob@domain.tld",
                  "tieringSeconds": 2592000,
                  "tieredMessageCount": 42,
                  "failedMessageCount": 3
                }""")
            .verify();
    }
}
