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

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.apache.james.dnsservice.api.DNSService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DkimDnsValidatorTest {

    private DNSService dnsService;
    private DkimDnsValidator validator;

    @BeforeEach
    void setUp() {
        dnsService = mock(DNSService.class);
        validator = new DkimDnsValidator(dnsService);
    }

    @Test
    void shouldBuildCorrectDkimRecordName() {
        String recordName = validator.buildDkimRecordName("s1", "example.com");
        assertThat(recordName).isEqualTo("s1._domainkey.example.com");
    }

    @Test
    void shouldValidateValidDkimRecord() {
        assertThat(validator.isValidDkimRecord("v=DKIM1; k=rsa; p=MIGfMA0GCS...")).isTrue();
        assertThat(validator.isValidDkimRecord("v=DKIM1;k=rsa;p=key")).isTrue();
        assertThat(validator.isValidDkimRecord("v=DKIM1 ; k=rsa ; p=key")).isTrue();
    }

    @Test
    void shouldRejectInvalidDkimRecord() {
        assertThat(validator.isValidDkimRecord("v=spf1 include:example.com")).isFalse();
        assertThat(validator.isValidDkimRecord("k=rsa; p=key")).isFalse();
        assertThat(validator.isValidDkimRecord("")).isFalse();
    }

    @Test
    void shouldPassWhenDkimRecordExists() throws Exception {
        when(dnsService.findTXTRecords("s1._domainkey.example.com"))
            .thenReturn(Arrays.asList("v=DKIM1; k=rsa; p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQ..."));

        Optional<String> result = validator.validate("example.com", "s1");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFailWhenNoDkimRecordFound() throws Exception {
        when(dnsService.findTXTRecords("s1._domainkey.example.com"))
            .thenReturn(Collections.emptyList());

        Optional<String> result = validator.validate("example.com", "s1");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("No DKIM record found");
    }

    @Test
    void shouldFailWhenDkimRecordIsInvalid() throws Exception {
        when(dnsService.findTXTRecords("s1._domainkey.example.com"))
            .thenReturn(Arrays.asList("k=rsa; p=key"));

        Optional<String> result = validator.validate("example.com", "s1");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("does not contain valid DKIM signature");
    }

    @Test
    void shouldHandleDnsQueryException() throws Exception {
        when(dnsService.findTXTRecords("s1._domainkey.example.com"))
            .thenThrow(new RuntimeException("DNS query failed"));

        Optional<String> result = validator.validate("example.com", "s1");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Failed to query DKIM record");
    }
}
