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

package com.linagora.tmail.rag.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PartitionTest {
    private static final Partition.Factory FACTORY = Partition.Factory.fromPattern("{localPart}.twake.{domainName}");

    @Test
    void forUsernameShouldSubstituteLocalPartAndDomain() {
        assertThat(FACTORY.forUsername(Username.of("bob@example.com")))
            .isEqualTo(new Partition("bob.twake.example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bob.twake.example.com/file/x", "x?y", "x#y", "x%2Fy", "x\\y"})
    void forUsernameShouldRejectPathDelimitingCharacters(String localPart) {
        Username username = Username.fromLocalPartWithDomain(localPart, Domain.of("example.com"));

        assertThatThrownBy(() -> FACTORY.forUsername(username))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldRejectControlCharacters() {
        assertThatThrownBy(() -> new Partition("bob\n.twake.example.com"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
