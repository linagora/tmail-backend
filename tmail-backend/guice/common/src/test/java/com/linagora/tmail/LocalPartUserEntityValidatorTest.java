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
package com.linagora.tmail;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalPartUserEntityValidatorTest {
    private final LocalPartUserEntityValidator testee = new LocalPartUserEntityValidator();

    @ParameterizedTest
    @ValueSource(strings = {"alice@tmail.local", "álice@tmail.local", "alice.bob-123@tmail.local", "alice"})
    void shouldAcceptRegularUsernames(String username) throws Exception {
        assertThat(testee.canCreate(Username.of(username))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"al­ice@tmail.local", "al​ice@tmail.local", "al‎ice@tmail.local",
        "al⁠ice@tmail.local", "﻿alice@tmail.local", "al\u0007ice@tmail.local", "al­ice"})
    void shouldRejectInvisibleCharacters(String username) throws Exception {
        assertThat(testee.canCreate(Username.of(username)))
            .hasValueSatisfying(failure -> assertThat(failure.errorMessage()).contains("invisible"));
    }

    @Test
    void shouldAcceptLocalPartOf64Octets() throws Exception {
        assertThat(testee.canCreate(Username.of("a".repeat(64) + "@tmail.local"))).isEmpty();
    }

    @Test
    void shouldRejectLocalPartLongerThan64Octets() throws Exception {
        assertThat(testee.canCreate(Username.of("a".repeat(65) + "@tmail.local")))
            .hasValueSatisfying(failure -> assertThat(failure.errorMessage()).contains("exceeds 64 octets"));
    }

    @Test
    void shouldCountOctetsRatherThanCharacters() throws Exception {
        assertThat(testee.canCreate(Username.of("á".repeat(33) + "@tmail.local")))
            .hasValueSatisfying(failure -> assertThat(failure.errorMessage()).contains("exceeds 64 octets"));
    }
}
