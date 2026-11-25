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

package com.linagora.tmail.james.jmap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OSEmailAddressContactSearchEngineAddressGramsTest {
    @Test
    void addressGramsShouldBeTheDistinctGramsOfTheGivenLength() {
        assertThat(OSEmailAddressContactSearchEngine.addressGrams("ab@abc", 2))
            .containsExactlyInAnyOrder("ab", "b@", "@a", "bc");
    }

    @Test
    void addressGramsShouldBeTheAddressItselfWhenNotLongerThanTheGramLength() {
        assertThat(OSEmailAddressContactSearchEngine.addressGrams("ab", 3))
            .containsExactly("ab");
    }
}
