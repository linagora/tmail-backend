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

package com.linagora.tmail.webadmin.jmap;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

class PopulateKeywordEmailQueryViewTaskAdditionalInformationDTOTest {
    private static final Instant INSTANT = Instant.parse("2007-12-03T10:15:30.00Z");
    private static final PopulateKeywordEmailQueryViewTask.AdditionalInformation DOMAIN_OBJECT = new PopulateKeywordEmailQueryViewTask.AdditionalInformation(
        INSTANT,
        1,
        2,
        3,
        4,
        5,
        RunningOptions.withMessageRatePerSecond(20));

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(PopulateKeywordEmailQueryViewTaskAdditionalInformationDTO.module())
            .bean(DOMAIN_OBJECT)
            .json(ClassLoaderUtils.getSystemResourceAsString("json/populateKeywordEmailQueryView.additionalInformation.json"))
            .verify();
    }
}
