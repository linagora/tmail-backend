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

package com.linagora.tmail.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.contact.ContactFields;

class JCardObjectTest {

    @Test
    void asContactFieldsShouldNotThrowIfFullnameNotProvided() {
        JCardObject cardObject = new JCardObject(Optional.empty(), maybeMailAddress("Jhon@doe.com"));
        assertThat(cardObject.asContactFields()).isNotEmpty();
    }

    @Test
    void asContactFieldsShouldConvertFieldsCorrectly() throws AddressException {
        JCardObject cardObject = new JCardObject(Optional.of("Jhon Doe"),
            List.of(new MailAddress("jhon@doe.com"), new MailAddress("jhon2@doe.com")));

        assertThat(cardObject.asContactFields())
            .containsExactlyInAnyOrder(new ContactFields(new MailAddress("jhon@doe.com"), "Jhon Doe", ""),
                new ContactFields(new MailAddress("jhon2@doe.com"), "Jhon Doe", ""));
    }

    @Test
    void shouldThrowIfFnOrEmailOptionalIsNull() {
        assertThatThrownBy(() -> new JCardObject(null, maybeMailAddress("jhon@doe.com")))
            .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new JCardObject(Optional.of("Jhon Doe"), null))
            .isInstanceOf(NullPointerException.class);
    }

    private List<MailAddress> maybeMailAddress(String email) {
        try {
            return List.of(new MailAddress(email));
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }
}
