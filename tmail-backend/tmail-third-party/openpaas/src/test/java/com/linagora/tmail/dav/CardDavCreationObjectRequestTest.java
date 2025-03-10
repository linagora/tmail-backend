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

package com.linagora.tmail.dav;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.dav.request.CardDavCreationObjectRequest;

import ezvcard.parameter.EmailType;

public class CardDavCreationObjectRequestTest {

    @Test
    void testToVCardWithAllFields() throws Exception {
        CardDavCreationObjectRequest.Email email = new CardDavCreationObjectRequest.Email(
            List.of(EmailType.HOME),
            new MailAddress("anbach4@lina.com"));

        CardDavCreationObjectRequest request = new CardDavCreationObjectRequest(
            "4.0",
            "123",
            Optional.of("An Bach4"),
            Optional.of(List.of("An", "Bach4")),
            email);

       assertThat(request.toVCard())
           .isEqualToNormalizingNewlines("""
                 BEGIN:VCARD
                 VERSION:4.0
                 FN:An Bach4
                 N:An;Bach4;;;
                 UID:123
                 EMAIL;TYPE=home:anbach4@lina.com
                 END:VCARD
                 """);
    }

    @Test
    void testToVCardWithoutOptionalFields() throws Exception {
        CardDavCreationObjectRequest.Email email = new CardDavCreationObjectRequest.Email(
            List.of(EmailType.WORK),
            new MailAddress("work@example.com"));

        CardDavCreationObjectRequest request = new CardDavCreationObjectRequest(
            "4.0",
            "uid123",
            Optional.empty(),
            Optional.empty(),
            email);

        assertThat(request.toVCard())
            .isEqualToNormalizingNewlines("""
                 BEGIN:VCARD
                 VERSION:4.0
                 UID:uid123
                 EMAIL;TYPE=work:work@example.com
                 END:VCARD
                 """);
    }
}
