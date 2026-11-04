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

package com.linagora.tmail.mailet;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CalendarUserAddressesTest {
    private static final String BOB = "bob@domain.tld";

    @ParameterizedTest
    @ValueSource(strings = {
        "mailto:bob@domain.tld",
        "MAILTO:bob@domain.tld",
        "bob@domain.tld",
        "mailto:bob@domain.tld?subject=hi",
        "mailto:bob@domain.tld?subject=hi&body=hello",
        "mailto:bob%40domain.tld"
    })
    void toMailAddressShouldDesignateTheMailtoAddress(String calAddress) throws Exception {
        assertThat(CalendarUserAddresses.toMailAddress(calAddress))
            .contains(new MailAddress(BOB));
    }

    @Test
    void toMailAddressShouldDesignateTheMailtoAddressOfAnUri() throws Exception {
        assertThat(CalendarUserAddresses.toMailAddress(URI.create("mailto:bob@domain.tld?subject=hi")))
            .contains(new MailAddress(BOB));
    }

    @Test
    void toMailAddressShouldKeepPlusSignOfTheLocalPart() throws Exception {
        assertThat(CalendarUserAddresses.toMailAddress("mailto:bob+tag@domain.tld"))
            .contains(new MailAddress("bob+tag@domain.tld"));
    }

    @Test
    void toMailAddressShouldNotTreatAnEncodedQuestionMarkAsHfieldsSeparator() {
        assertThat(CalendarUserAddresses.toMailAddress("mailto:bob%3Fsubject=hi@domain.tld"))
            .hasValueSatisfying(address -> assertThat(address.getLocalPart()).isEqualTo("bob?subject=hi"));
    }

    @Test
    void toMailAddressShouldKeepQuestionMarkOfASchemeLessAddress() {
        assertThat(CalendarUserAddresses.toMailAddress("bob?tag@domain.tld"))
            .hasValueSatisfying(address -> assertThat(address.getLocalPart()).isEqualTo("bob?tag"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "bob@domain.tld?subject=hi",
        "http://domain.tld/bob@domain.tld",
        "urn:bob@domain.tld",
        "mailto:bob%zz@domain.tld",
        "mailto:not an address",
        "mailto:",
        "mailto:?subject=hi"
    })
    void toMailAddressShouldRejectInvalidCalendarUserAddresses(String calAddress) {
        assertThat(CalendarUserAddresses.toMailAddress(calAddress)).isEmpty();
    }

    @Test
    void toMailAddressShouldRejectNullUri() {
        assertThat(CalendarUserAddresses.toMailAddress((URI) null)).isEmpty();
    }
}
