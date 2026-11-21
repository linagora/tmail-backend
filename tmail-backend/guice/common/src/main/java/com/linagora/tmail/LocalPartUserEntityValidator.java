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

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.james.UserEntityValidator;
import org.apache.james.core.Username;

/**
 * Rejects local parts that cannot be told apart from another address when displayed (invisible
 * format or control characters such as the soft hyphen U+00AD or the zero width space U+200B) and
 * local parts exceeding the 64 octets allowed by RFC 5321 section 4.5.3.1.1.
 */
public class LocalPartUserEntityValidator implements UserEntityValidator {
    public static final int MAX_LOCAL_PART_OCTETS = 64;

    @Override
    public Optional<ValidationFailure> canCreate(Username username, Set<EntityType> ignoredTypes) {
        return Stream.of(checkNoInvisibleCharacter(username), checkLength(username))
            .flatMap(Optional::stream)
            .findFirst();
    }

    private Optional<ValidationFailure> checkNoInvisibleCharacter(Username username) {
        return Optional.of(username.getLocalPart())
            .filter(localPart -> localPart.codePoints().anyMatch(this::isInvisible))
            .map(localPart -> new ValidationFailure(String.format("'%s' is not a valid username: its local part contains invisible (format or control) characters",
                username.asString())));
    }

    private boolean isInvisible(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.FORMAT || type == Character.CONTROL;
    }

    private Optional<ValidationFailure> checkLength(Username username) {
        return Optional.of(username.getLocalPart())
            .filter(localPart -> localPart.getBytes(StandardCharsets.UTF_8).length > MAX_LOCAL_PART_OCTETS)
            .map(localPart -> new ValidationFailure(String.format("'%s' is not a valid username: its local part exceeds %d octets",
                username.asString(), MAX_LOCAL_PART_OCTETS)));
    }
}
