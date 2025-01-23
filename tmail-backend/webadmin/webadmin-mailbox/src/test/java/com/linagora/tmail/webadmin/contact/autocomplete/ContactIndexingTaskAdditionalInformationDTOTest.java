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

package com.linagora.tmail.webadmin.contact.autocomplete;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.webadmin.contact.aucomplete.ContactIndexingTask;
import com.linagora.tmail.webadmin.contact.aucomplete.ContactIndexingTaskAdditionalInformationDTO;

public class ContactIndexingTaskAdditionalInformationDTOTest {

    @Test
    void beanTest() throws Exception {
        JsonSerializationVerifier.dtoModule(ContactIndexingTaskAdditionalInformationDTO.module())
            .bean(new ContactIndexingTask.Details(Instant.parse("2007-12-03T10:15:30.00Z"),
                1,
                2,
                3,
                ImmutableList.of("bob", "alice"),
                ContactIndexingTask.RunningOptions.DEFAULT))
            .json("""
                {
                  "failedContactsCount": 3,
                  "failedUsers": ["bob","alice"],
                  "indexedContactsCount": 2,
                  "processedUsersCount": 1,
                  "runningOptions": {
                    "usersPerSecond": 1
                  },
                  "timestamp": "2007-12-03T10:15:30Z",
                  "type": "ContactIndexing"
                }""")
            .verify();
    }
}
