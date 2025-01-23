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

import static org.mockito.Mockito.mock;

import org.apache.james.JsonSerializationVerifier;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.webadmin.contact.aucomplete.ContactIndexingService;
import com.linagora.tmail.webadmin.contact.aucomplete.ContactIndexingTask;
import com.linagora.tmail.webadmin.contact.aucomplete.ContactIndexingTaskDTO;

public class ContactIndexingTaskSerializationTest {

    private final ContactIndexingService contactIndexingService = mock(ContactIndexingService.class);

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(ContactIndexingTaskDTO.module(contactIndexingService))
            .bean(new ContactIndexingTask(contactIndexingService, ContactIndexingTask.RunningOptions.of(9)))
            .json("""
                {
                  "runningOptions": {
                    "usersPerSecond": 9
                  },
                  "type": "ContactIndexing"
                }""")
            .verify();
    }
}
