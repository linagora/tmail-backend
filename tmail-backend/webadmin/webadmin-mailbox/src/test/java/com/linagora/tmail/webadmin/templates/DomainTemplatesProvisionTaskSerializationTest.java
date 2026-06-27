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
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class DomainTemplatesProvisionTaskSerializationTest {
    private final TemplatesProvisionService service = mock(TemplatesProvisionService.class);

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(DomainTemplatesProvisionTaskDTO.module(service))
            .bean(new DomainTemplatesProvisionTask(service,
                Domain.of("domain.tld"),
                Username.of("templates@domain.tld"),
                "Templates",
                new ProvisionOptions(true, false),
                10))
            .json("""
                {
                  "type": "DomainTemplatesProvisionTask",
                  "domain": "domain.tld",
                  "sourceUser": "templates@domain.tld",
                  "folderName": "Templates",
                  "overwriteExisting": true,
                  "prune": false,
                  "usersPerSecond": 10
                }""")
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(DomainTemplatesProvisionTaskAdditionalInformationDTO.module())
            .bean(new DomainTemplatesProvisionTask.AdditionalInformation(
                Instant.parse("2007-12-03T10:15:30.00Z"),
                "domain.tld",
                "templates@domain.tld",
                "Templates",
                true,
                false,
                10,
                100L,
                200L,
                5L,
                3L,
                ImmutableList.of("bob@domain.tld")))
            .json("""
                {
                  "type": "DomainTemplatesProvisionTask",
                  "timestamp": "2007-12-03T10:15:30Z",
                  "domain": "domain.tld",
                  "sourceUser": "templates@domain.tld",
                  "folderName": "Templates",
                  "overwriteExisting": true,
                  "prune": false,
                  "usersPerSecond": 10,
                  "processedUsers": 100,
                  "appliedTemplates": 200,
                  "skippedTemplates": 5,
                  "removedTemplates": 3,
                  "failedUsers": ["bob@domain.tld"]
                }""")
            .verify();
    }
}
