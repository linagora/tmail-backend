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
import java.util.Set;

import org.apache.james.dnsservice.api.DNSService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpfDnsValidatorTest {

    private DNSService dnsService;

    @BeforeEach
    void setUp() {
        dnsService = mock(DNSService.class);
    }

    @Test
    void shouldParseRequiredIps() {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "192.0.2.10,198.51.100.5");
        Set<String> ips = validator.parseRequiredIps("192.0.2.10,198.51.100.5");

        assertThat(ips).containsExactlyInAnyOrder("192.0.2.10", "198.51.100.5");
    }

    @Test
    void shouldParseRequiredIpsWithSpaces() {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "192.0.2.10 , 198.51.100.5");
        Set<String> ips = validator.parseRequiredIps("192.0.2.10 , 198.51.100.5");

        assertThat(ips).containsExactlyInAnyOrder("192.0.2.10", "198.51.100.5");
    }

    @Test
    void shouldIdentifySpfRecord() {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "192.0.2.10");

        assertThat(validator.isSpfRecord("v=spf1 ip4:192.0.2.10 ~all")).isTrue();
        assertThat(validator.isSpfRecord("v=spf1 include:_spf.google.com ~all")).isTrue();
        assertThat(validator.isSpfRecord("  v=spf1 ip4:192.0.2.10")).isTrue();
        assertThat(validator.isSpfRecord("v=DKIM1; k=rsa")).isFalse();
        assertThat(validator.isSpfRecord("random text")).isFalse();
    }

    @Test
    void shouldExtractIpsFromSpfRecord() {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "192.0.2.10");
        Set<String> ips = validator.extractIpsFromSpfRecord("v=spf1 ip4:192.0.2.10 ip4:198.51.100.0/24 include:_spf.google.com ~all");

        assertThat(ips).containsExactlyInAnyOrder("192.0.2.10", "198.51.100.0/24");
    }

    @Test
    void shouldCheckIpAuthorization() {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "192.0.2.10");

        // Exact match
        assertThat(validator.isIpAuthorized("192.0.2.10", Set.of("192.0.2.10", "192.0.2.11"))).isTrue();

        // IP within CIDR range
        assertThat(validator.isIpAuthorized("192.0.2.10", Set.of("192.0.2.0/24"))).isTrue();
        assertThat(validator.isIpAuthorized("192.0.2.255", Set.of("192.0.2.0/24"))).isTrue();
        assertThat(validator.isIpAuthorized("192.0.3.1", Set.of("192.0.2.0/24"))).isFalse();
    }

    @Test
    void shouldValidateCidrRanges() {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "192.0.2.10");

        // /24 range
        assertThat(validator.isIpInCidrRange("192.0.2.10", "192.0.2.0/24")).isTrue();
        assertThat(validator.isIpInCidrRange("192.0.2.255", "192.0.2.0/24")).isTrue();
        assertThat(validator.isIpInCidrRange("192.0.3.0", "192.0.2.0/24")).isFalse();

        // /32 range (single IP)
        assertThat(validator.isIpInCidrRange("192.0.2.10", "192.0.2.10/32")).isTrue();
        assertThat(validator.isIpInCidrRange("192.0.2.11", "192.0.2.10/32")).isFalse();
    }

    @Test
    void shouldPassWhenSpfContainsRequiredIps() throws Exception {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "192.0.2.10,198.51.100.5");

        when(dnsService.findTXTRecords("example.com"))
            .thenReturn(Arrays.asList("v=spf1 ip4:192.0.2.10 ip4:198.51.100.5 ~all"));

        Optional<String> result = validator.validate("example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldPassWhenSpfContainsRequiredIpsInCidr() throws Exception {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "192.0.2.10,192.0.2.11");

        when(dnsService.findTXTRecords("example.com"))
            .thenReturn(Arrays.asList("v=spf1 ip4:192.0.2.0/24 ~all"));

        Optional<String> result = validator.validate("example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldAllowAdditionalIpsInSpf() throws Exception {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "192.0.2.10");

        when(dnsService.findTXTRecords("example.com"))
            .thenReturn(Arrays.asList("v=spf1 ip4:192.0.2.10 ip4:203.0.113.0/24 include:_spf.google.com ~all"));

        Optional<String> result = validator.validate("example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFailWhenSpfMissingRequiredIp() throws Exception {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "192.0.2.10,198.51.100.5");

        when(dnsService.findTXTRecords("example.com"))
            .thenReturn(Arrays.asList("v=spf1 ip4:192.0.2.10 ~all"));

        Optional<String> result = validator.validate("example.com");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("missing required IPs");
        assertThat(result.get()).contains("198.51.100.5");
    }

    @Test
    void shouldFailWhenNoSpfRecord() throws Exception {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "192.0.2.10");

        when(dnsService.findTXTRecords("example.com"))
            .thenReturn(Collections.emptyList());

        Optional<String> result = validator.validate("example.com");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("No TXT records found");
    }

    @Test
    void shouldFailWhenNoValidSpfRecord() throws Exception {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "192.0.2.10");

        when(dnsService.findTXTRecords("example.com"))
            .thenReturn(Arrays.asList("v=DKIM1; k=rsa; p=key"));

        Optional<String> result = validator.validate("example.com");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("No SPF record found");
    }
}
