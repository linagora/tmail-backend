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
import com.linagora.tmail.mailet.dns.DnsValidationFailure.SpfValidationFailure;

class SpfDnsValidatorTest {

    private DNSService dnsService;

    @BeforeEach
    void setUp() {
        dnsService = mock(DNSService.class);
    }

    @Test
    void shouldIdentifySpfRecord() {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "_spf.twake.app");

        assertThat(validator.isSpfRecord("v=spf1 ip4:192.0.2.10 ~all")).isTrue();
        assertThat(validator.isSpfRecord("v=spf1 include:_spf.google.com ~all")).isTrue();
        assertThat(validator.isSpfRecord("  v=spf1 ip4:192.0.2.10")).isTrue();
        assertThat(validator.isSpfRecord("v=DKIM1; k=rsa")).isFalse();
        assertThat(validator.isSpfRecord("random text")).isFalse();
    }


    @Test
    void shouldPassWhenSpfContainsRequiredInclude() {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "_spf.twake.app");

        when(dnsService.findTXTRecords("example.com"))
            .thenReturn(ImmutableList.of("v=spf1 include:_spf.twake.app ~all"));

        Optional<SpfValidationFailure> result = validator.validate(Domain.of("example.com"));

        assertThat(result).isEmpty();
    }


    @Test
    void shouldFailWhenSpfMissingRequiredInclude() {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "_spf.twake.app");

        when(dnsService.findTXTRecords("example.com"))
            .thenReturn(ImmutableList.of("v=spf1 ip4:192.0.2.10 ~all"));

        Optional<SpfValidationFailure> result = validator.validate(Domain.of("example.com"));

        assertThat(result).isPresent();
        assertThat(result.get().message()).contains("does not include required SPF configuration");
        assertThat(result.get().message()).contains("_spf.twake.app");
    }

    @Test
    void shouldFailWhenNoSpfRecord() {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "_spf.twake.app");

        when(dnsService.findTXTRecords("example.com"))
            .thenReturn(Collections.emptyList());

        Optional<SpfValidationFailure> result = validator.validate(Domain.of("example.com"));

        assertThat(result).isPresent();
        assertThat(result.get().message()).contains("No TXT records found");
    }

    @Test
    void shouldFailWhenNoValidSpfRecord() {
        SpfDnsValidator validator = new SpfDnsValidator(dnsService, "_spf.twake.app");

        when(dnsService.findTXTRecords("example.com"))
            .thenReturn(ImmutableList.of("v=DKIM1; k=rsa; p=key"));

        Optional<SpfValidationFailure> result = validator.validate(Domain.of("example.com"));

        assertThat(result).isPresent();
        assertThat(result.get().message()).contains("No SPF record found");
    }
}
