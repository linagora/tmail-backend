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
 *******************************************************************/

package com.linagora.tmail.james.jmap.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.event.SignatureTextFactory.SignatureText;

class SignatureTextTest {

    @Test
    void interpolateShouldReturnUnchangedWhenNoPlaceholders() {
        SignatureText signature = new SignatureText("Hello world", "<p>Hello world</p>");
        assertThat(signature.interpolate(Map.of())).isEqualTo(signature);
    }

    @Test
    void interpolateShouldSubstituteSingleAttribute() {
        SignatureText signature = new SignatureText("Hello {ldap:givenName}", "<p>Hello {ldap:givenName}</p>");
        assertThat(signature.interpolate(Map.of("givenName", "John")))
            .isEqualTo(new SignatureText("Hello John", "<p>Hello John</p>"));
    }

    @Test
    void interpolateShouldSubstituteMultipleDistinctAttributes() {
        SignatureText signature = new SignatureText("{ldap:givenName} {ldap:sn}", "<p>{ldap:givenName} {ldap:sn}</p>");
        assertThat(signature.interpolate(Map.of("givenName", "John", "sn", "Doe")))
            .isEqualTo(new SignatureText("John Doe", "<p>John Doe</p>"));
    }

    @Test
    void interpolateShouldLeaveUnknownPlaceholdersAsIs() {
        SignatureText signature = new SignatureText("Hello {ldap:unknown}", "<p>Hello {ldap:unknown}</p>");
        assertThat(signature.interpolate(Map.of("givenName", "John")))
            .isEqualTo(signature);
    }

    @Test
    void interpolateShouldSubstituteRepeatedAttributes() {
        SignatureText signature = new SignatureText("{ldap:givenName} {ldap:givenName}", "<p>{ldap:givenName}</p>");
        assertThat(signature.interpolate(Map.of("givenName", "John")))
            .isEqualTo(new SignatureText("John John", "<p>John</p>"));
    }

    @Test
    void interpolateShouldLeaveAllPlaceholdersWhenMapIsEmpty() {
        SignatureText signature = new SignatureText("Hello {ldap:givenName}", "<p>Hello</p>");
        assertThat(signature.interpolate(Map.of()))
            .isEqualTo(signature);
    }

    @Test
    void interpolateShouldHandleNullTextSignature() {
        SignatureText signature = new SignatureText(null, "<p>{ldap:givenName}</p>");
        assertThat(signature.interpolate(Map.of("givenName", "John")))
            .isEqualTo(new SignatureText(null, "<p>John</p>"));
    }

    @Test
    void interpolateShouldHandleNullHtmlSignature() {
        SignatureText signature = new SignatureText("{ldap:givenName}", null);
        assertThat(signature.interpolate(Map.of("givenName", "John")))
            .isEqualTo(new SignatureText("John", null));
    }

    @Test
    void interpolateShouldNotInterfereWithNonLdapCurlyBraces() {
        SignatureText signature = new SignatureText("Hello {notLdap:attr}", "<p>Hello {other}</p>");
        assertThat(signature.interpolate(Map.of("attr", "John")))
            .isEqualTo(signature);
    }

    @Test
    void interpolateShouldHandleSpecialRegexCharactersInReplacementValue() {
        SignatureText signature = new SignatureText("{ldap:givenName}", "<p>{ldap:givenName}</p>");
        assertThat(signature.interpolate(Map.of("givenName", "John$1")))
            .isEqualTo(new SignatureText("John$1", "<p>John$1</p>"));
    }

    @Test
    void interpolateShouldSubstituteAttributeOnlyInMatchingSide() {
        SignatureText signature = new SignatureText("plain: {ldap:sn}", "html: {ldap:givenName}");
        assertThat(signature.interpolate(Map.of("sn", "Doe", "givenName", "John")))
            .isEqualTo(new SignatureText("plain: Doe", "html: John"));
    }
}
