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

package com.linagora.tmail.mailet.dns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.mailet.dns.DmarcDnsValidator.DmarcPolicy;
import com.linagora.tmail.mailet.dns.DnsValidationFailure.DmarcValidationFailure;

class DmarcDnsValidatorTest {

    private DNSService dnsService;

    @BeforeEach
    void setUp() {
        dnsService = mock(DNSService.class);
    }

    @Test
    void shouldBuildCorrectDmarcRecordName() {
        DmarcDnsValidator validator = new DmarcDnsValidator(dnsService, "quarantine");

        assertThat(validator.buildDmarcRecordName(Domain.of("example.com"))).isEqualTo("_dmarc.example.com");
    }

    @Test
    void shouldIdentifyDmarcRecord() {
        DmarcDnsValidator validator = new DmarcDnsValidator(dnsService, "quarantine");

        assertThat(validator.isDmarcRecord("v=DMARC1; p=quarantine; rua=mailto:dmarc@example.com")).isTrue();
        assertThat(validator.isDmarcRecord("v=DMARC1;p=reject")).isTrue();
        assertThat(validator.isDmarcRecord("  v=DMARC1 ; p=none")).isTrue();
        // Test with spaces around version tag
        assertThat(validator.isDmarcRecord("v = DMARC1; p=quarantine")).isTrue();
        assertThat(validator.isDmarcRecord(" v =DMARC1 ")).isTrue();
        // Test case insensitive
        assertThat(validator.isDmarcRecord("V=DMARC1; p=none")).isTrue();
        // Invalid records
        assertThat(validator.isDmarcRecord("v=spf1 include:example.com")).isFalse();
        assertThat(validator.isDmarcRecord("p=quarantine")).isFalse();
    }

    @Test
    void shouldExtractDmarcPolicy() {
        DmarcDnsValidator validator = new DmarcDnsValidator(dnsService, "quarantine");

        assertThat(validator.extractPolicy("v=DMARC1; p=quarantine; rua=mailto:test@example.com"))
            .isPresent()
            .contains(DmarcPolicy.QUARANTINE);

        assertThat(validator.extractPolicy("v=DMARC1; p=reject"))
            .isPresent()
            .contains(DmarcPolicy.REJECT);

        assertThat(validator.extractPolicy("v=DMARC1; p=none"))
            .isPresent()
            .contains(DmarcPolicy.NONE);

        assertThat(validator.extractPolicy("v=DMARC1; rua=mailto:test@example.com"))
            .isEmpty();
    }

    @Test
    void shouldComparePolicyStrictness() {
        assertThat(DmarcPolicy.REJECT.isStricterOrEqualTo(DmarcPolicy.QUARANTINE)).isTrue();
        assertThat(DmarcPolicy.REJECT.isStricterOrEqualTo(DmarcPolicy.REJECT)).isTrue();
        assertThat(DmarcPolicy.QUARANTINE.isStricterOrEqualTo(DmarcPolicy.NONE)).isTrue();
        assertThat(DmarcPolicy.QUARANTINE.isStricterOrEqualTo(DmarcPolicy.QUARANTINE)).isTrue();
        assertThat(DmarcPolicy.NONE.isStricterOrEqualTo(DmarcPolicy.QUARANTINE)).isFalse();
        assertThat(DmarcPolicy.QUARANTINE.isStricterOrEqualTo(DmarcPolicy.REJECT)).isFalse();
    }

    @Test
    void shouldPassWhenDmarcPolicyIsQuarantine() {
        DmarcDnsValidator validator = new DmarcDnsValidator(dnsService, "quarantine");

        when(dnsService.findTXTRecords("_dmarc.example.com"))
            .thenReturn(ImmutableList.of("v=DMARC1; p=quarantine; rua=mailto:dmarc@example.com"));

        Optional<DmarcValidationFailure> result = validator.validate(Domain.of("example.com"));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldPassWhenDmarcPolicyIsReject() {
        DmarcDnsValidator validator = new DmarcDnsValidator(dnsService, "quarantine");

        when(dnsService.findTXTRecords("_dmarc.example.com"))
            .thenReturn(ImmutableList.of("v=DMARC1; p=reject"));

        Optional<DmarcValidationFailure> result = validator.validate(Domain.of("example.com"));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFailWhenDmarcPolicyIsTooLenient() {
        DmarcDnsValidator validator = new DmarcDnsValidator(dnsService, "quarantine");

        when(dnsService.findTXTRecords("_dmarc.example.com"))
            .thenReturn(ImmutableList.of("v=DMARC1; p=none"));

        Optional<DmarcValidationFailure> result = validator.validate(Domain.of("example.com"));

        assertThat(result).isPresent();
        assertThat(result.get().message()).contains("too lenient");
        assertThat(result.get().message()).contains("Required: quarantine");
        assertThat(result.get().message()).contains("Found: none");
    }

    @Test
    void shouldFailWhenNoDmarcRecord() {
        DmarcDnsValidator validator = new DmarcDnsValidator(dnsService, "quarantine");

        when(dnsService.findTXTRecords("_dmarc.example.com"))
            .thenReturn(Collections.emptyList());

        Optional<DmarcValidationFailure> result = validator.validate(Domain.of("example.com"));

        assertThat(result).isPresent();
        assertThat(result.get().message()).contains("No DMARC record found");
    }

    @Test
    void shouldFailWhenDmarcRecordHasNoPolicy() {
        DmarcDnsValidator validator = new DmarcDnsValidator(dnsService, "quarantine");

        when(dnsService.findTXTRecords("_dmarc.example.com"))
            .thenReturn(ImmutableList.of("v=DMARC1; rua=mailto:dmarc@example.com"));

        Optional<DmarcValidationFailure> result = validator.validate(Domain.of("example.com"));

        assertThat(result).isPresent();
        assertThat(result.get().message()).contains("does not contain a valid policy");
    }

    @Test
    void shouldRejectInvalidMinimumPolicy() {
        assertThatThrownBy(() -> new DmarcDnsValidator(dnsService, "invalid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid DMARC policy");
    }
}
