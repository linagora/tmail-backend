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

import static org.mockito.Mockito.mock;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PopulateKeywordEmailQueryViewTaskSerializationTest {
    private KeywordEmailQueryViewPopulator populator;

    @BeforeEach
    void setUp() {
        populator = mock(KeywordEmailQueryViewPopulator.class);
    }

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(PopulateKeywordEmailQueryViewTaskDTO.module(populator))
            .bean(new PopulateKeywordEmailQueryViewTask(populator,
                RunningOptions.withMessageRatePerSecond(2)))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/populateKeywordEmailQueryView.task.json"))
            .verify();
    }
}
