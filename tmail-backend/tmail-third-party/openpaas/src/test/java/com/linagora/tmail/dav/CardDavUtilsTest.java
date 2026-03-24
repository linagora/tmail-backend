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

import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.dav.request.CardDavCreationObjectRequest;

public class CardDavUtilsTest {

    @Test
    void createObjectCreationRequestShouldNormalizeSubAddressedRecipient() throws Exception {
        MailAddress subAddressed = new MailAddress("bob+folder@domain.tld");
        MailAddress plain = new MailAddress("bob@domain.tld");

        CardDavCreationObjectRequest requestFromSubAddressed = CardDavUtils.createObjectCreationRequest(subAddressed);
        CardDavCreationObjectRequest requestFromPlain = CardDavUtils.createObjectCreationRequest(plain);

        assertThat(requestFromSubAddressed.uid()).isEqualTo(requestFromPlain.uid());
    }

    @Test
    void createObjectCreationRequestShouldStoreNormalizedEmailAddress() throws Exception {
        MailAddress subAddressed = new MailAddress("bob+folder@domain.tld");

        CardDavCreationObjectRequest request = CardDavUtils.createObjectCreationRequest(subAddressed);

        assertThat(request.email().value().asString()).isEqualTo("bob@domain.tld");
    }

    @Test
    void createObjectCreationRequestWithFullNameShouldNormalizeSubAddressedRecipient() throws Exception {
        MailAddress subAddressed = new MailAddress("bob+project@domain.tld");
        MailAddress plain = new MailAddress("bob@domain.tld");

        CardDavCreationObjectRequest requestFromSubAddressed = CardDavUtils.createObjectCreationRequest(Optional.of("Bob"), subAddressed);
        CardDavCreationObjectRequest requestFromPlain = CardDavUtils.createObjectCreationRequest(Optional.of("Bob"), plain);

        assertThat(requestFromSubAddressed.uid()).isEqualTo(requestFromPlain.uid());
    }

    @Test
    void createObjectCreationRequestShouldNotAlterPlainAddress() throws Exception {
        MailAddress plain = new MailAddress("bob@domain.tld");

        CardDavCreationObjectRequest request = CardDavUtils.createObjectCreationRequest(plain);

        assertThat(request.email().value().asString()).isEqualTo("bob@domain.tld");
    }
}
