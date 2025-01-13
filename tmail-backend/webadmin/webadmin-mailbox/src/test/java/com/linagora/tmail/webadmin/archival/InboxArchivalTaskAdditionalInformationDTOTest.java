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

package com.linagora.tmail.webadmin.archival;

import java.time.Instant;
import java.util.Set;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

class InboxArchivalTaskAdditionalInformationDTOTest {
    @Test
    void beanTest() throws Exception {
        JsonSerializationVerifier.dtoModule(InboxArchivalTaskAdditionalInformationDTO.SERIALIZATION_MODULE)
            .bean(new InboxArchivalTask.AdditionalInformation(Instant.parse("2007-12-03T10:15:30.00Z"), 4, 2,
                1, 1, Set.of(Username.of("bob@domain.tld"))))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/inboxArchivalTask.additionalInformation.json"))
            .verify();
    }
}
