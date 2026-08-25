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

package com.linagora.tmail.migration.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

class MigrationSwitchMessageTest {
    @Test
    void shouldParseUsernameFromValidPayload() {
        byte[] payload = "{\"migratedUser\":{\"mailAddress\": \"btellier@linagora.com\"}}".getBytes(UTF_8);

        assertThat(MigrationSwitchMessage.parseUsername(payload))
            .contains(Username.of("btellier@linagora.com"));
    }

    @Test
    void shouldIgnoreUnknownProperties() {
        byte[] payload = "{\"migratedUser\":{\"mailAddress\": \"btellier@linagora.com\", \"extra\": \"ignored\"}, \"other\": 1}".getBytes(UTF_8);

        assertThat(MigrationSwitchMessage.parseUsername(payload))
            .contains(Username.of("btellier@linagora.com"));
    }

    @Test
    void shouldReturnEmptyWhenPayloadIsNotJson() {
        assertThat(MigrationSwitchMessage.parseUsername("not json".getBytes(UTF_8))).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenMigratedUserIsMissing() {
        assertThat(MigrationSwitchMessage.parseUsername("{}".getBytes(UTF_8))).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenMailAddressIsMissing() {
        byte[] payload = "{\"migratedUser\":{}}".getBytes(UTF_8);

        assertThat(MigrationSwitchMessage.parseUsername(payload)).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenMailAddressIsInvalid() {
        byte[] payload = "{\"migratedUser\":{\"mailAddress\": \"not-an-address\"}}".getBytes(UTF_8);

        assertThat(MigrationSwitchMessage.parseUsername(payload)).isEmpty();
    }
}
