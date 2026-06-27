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

package com.linagora.tmail.webadmin.templates;

import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class UserTemplatesProvisionTaskSerializationTest {
    private final TemplatesProvisionService service = mock(TemplatesProvisionService.class);

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(UserTemplatesProvisionTaskDTO.module(service))
            .bean(new UserTemplatesProvisionTask(service,
                Username.of("templates@domain.tld"),
                Username.of("bob@domain.tld"),
                "Templates",
                new ProvisionOptions(false, true)))
            .json("""
                {
                  "type": "UserTemplatesProvisionTask",
                  "sourceUser": "templates@domain.tld",
                  "targetUser": "bob@domain.tld",
                  "folderName": "Templates",
                  "overwriteExisting": false,
                  "prune": true
                }""")
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(UserTemplatesProvisionTaskAdditionalInformationDTO.module())
            .bean(new UserTemplatesProvisionTask.AdditionalInformation(
                Instant.parse("2007-12-03T10:15:30.00Z"),
                "templates@domain.tld",
                "bob@domain.tld",
                "Templates",
                false,
                true,
                1L,
                4L,
                1L,
                2L,
                ImmutableList.of()))
            .json("""
                {
                  "type": "UserTemplatesProvisionTask",
                  "timestamp": "2007-12-03T10:15:30Z",
                  "sourceUser": "templates@domain.tld",
                  "targetUser": "bob@domain.tld",
                  "folderName": "Templates",
                  "overwriteExisting": false,
                  "prune": true,
                  "processedUsers": 1,
                  "appliedTemplates": 4,
                  "skippedTemplates": 1,
                  "removedTemplates": 2,
                  "failedUsers": []
                }""")
            .verify();
    }
}
