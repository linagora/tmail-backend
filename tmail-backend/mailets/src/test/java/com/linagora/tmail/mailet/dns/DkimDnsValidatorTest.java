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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.mailet.dns.DnsValidationFailure.DkimValidationFailure;

class DkimDnsValidatorTest {

    private DNSService dnsService;
    private DkimDnsValidator validator;

    @BeforeEach
    void setUp() {
        dnsService = mock(DNSService.class);
        validator = new DkimDnsValidator(dnsService, ImmutableList.of("key1", "key2"));
    }

    @Test
    void shouldBuildCorrectDkimRecordName() {
        String recordName = validator.buildDkimRecordName("s1", Domain.of("example.com"));
        assertThat(recordName).isEqualTo("s1._domainkey.example.com");
    }

    @Test
    void shouldValidateValidDkimRecord() {
        assertThat(validator.isValidDkimRecord("v=DKIM1; k=rsa; p=MIGfMA0GCS...")).isTrue();
        assertThat(validator.isValidDkimRecord("v=DKIM1;k=rsa;p=key")).isTrue();
        assertThat(validator.isValidDkimRecord("v=DKIM1 ; k=rsa ; p=key")).isTrue();
        // Test with spaces around version tag
        assertThat(validator.isValidDkimRecord("v = DKIM1; k=rsa; p=key")).isTrue();
        assertThat(validator.isValidDkimRecord(" v=DKIM1; k=rsa")).isTrue();
        // Test case insensitive
        assertThat(validator.isValidDkimRecord("V=DKIM1; k=rsa")).isTrue();
    }

    @Test
    void shouldRejectInvalidDkimRecord() {
        assertThat(validator.isValidDkimRecord("v=spf1 include:example.com")).isFalse();
        assertThat(validator.isValidDkimRecord("k=rsa; p=key")).isFalse();
        assertThat(validator.isValidDkimRecord("")).isFalse();
    }

    @Test
    void shouldPassWhenDkimRecordExists() {
        when(dnsService.findTXTRecords("s1._domainkey.example.com"))
            .thenReturn(ImmutableList.of("v=DKIM1; k=rsa; p=key1"));

        Optional<DkimValidationFailure> result = validator.validate(Domain.of("example.com"), "s1");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldPassWhenSecondKeyUsed() {
        when(dnsService.findTXTRecords("s1._domainkey.example.com"))
            .thenReturn(ImmutableList.of("v=DKIM1; k=rsa; p=key2"));

        Optional<DkimValidationFailure> result = validator.validate(Domain.of("example.com"), "s1");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFailWhenNotAcceptedKey() {
        when(dnsService.findTXTRecords("s1._domainkey.example.com"))
            .thenReturn(ImmutableList.of("v=DKIM1; k=rsa; p=key3"));

        Optional<DkimValidationFailure> result = validator.validate(Domain.of("example.com"), "s1");

        assertThat(result).isPresent();
        assertThat(result.get().message()).isEqualTo("DKIM record at s1._domainkey.example.com does not contain valid DKIM signature (must start with v=DKIM1 and have a valid public key)");
    }

    @Test
    void shouldFailWhenNoDkimRecordFound() {
        when(dnsService.findTXTRecords("s1._domainkey.example.com"))
            .thenReturn(Collections.emptyList());

        Optional<DkimValidationFailure> result = validator.validate(Domain.of("example.com"), "s1");

        assertThat(result.get().message()).contains("No DKIM record found");
    }

    @Test
    void shouldFailWhenDkimRecordIsInvalid() {
        when(dnsService.findTXTRecords("s1._domainkey.example.com"))
            .thenReturn(ImmutableList.of("k=rsa; p=key"));

        Optional<DkimValidationFailure> result = validator.validate(Domain.of("example.com"), "s1");

        assertThat(result).isPresent();
        assertThat(result.get().message()).contains("does not contain valid DKIM signature");
    }

    @Test
    void shouldHandleDnsQueryException() {
        when(dnsService.findTXTRecords("s1._domainkey.example.com"))
            .thenThrow(new RuntimeException("DNS query failed"));

        Optional<DkimValidationFailure> result = validator.validate(Domain.of("example.com"), "s1");

        assertThat(result).isPresent();
        assertThat(result.get().message()).contains("Failed to query DKIM record");
    }
}
