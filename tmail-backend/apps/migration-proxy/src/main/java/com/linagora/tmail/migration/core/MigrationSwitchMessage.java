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

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code {"migratedUser":{"mailAddress": "btellier@linagora.com"}}} payload carried by the
 * {@code migration:switch} AMQP exchange (see {@link MigrationSwitchConsumer}).
 */
public record MigrationSwitchMessage(@JsonProperty("migratedUser") MigratedUser migratedUser) {
    public record MigratedUser(@JsonProperty("mailAddress") String mailAddress) {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationSwitchMessage.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Parses the AMQP payload into the {@link Username} to switch, logging and returning empty on a
     * malformed payload or an unparsable mail address rather than throwing: such a message will never
     * succeed on retry, so the caller is expected to drop it after logging instead of dead-lettering it.
     */
    public static Optional<Username> parseUsername(byte[] payload) {
        try {
            MigrationSwitchMessage message = OBJECT_MAPPER.readValue(payload, MigrationSwitchMessage.class);
            MailAddress mailAddress = new MailAddress(message.migratedUser().mailAddress());
            return Optional.of(Username.fromMailAddress(mailAddress));
        } catch (Exception e) {
            LOGGER.warn("Dropping unparsable migration switch message: {}", new String(payload, StandardCharsets.UTF_8), e);
            return Optional.empty();
        }
    }
}
