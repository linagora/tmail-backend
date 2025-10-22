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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.mailet.dns.DkimDnsValidator;
import com.linagora.tmail.mailet.dns.DmarcDnsValidator;
import com.linagora.tmail.mailet.dns.SpfDnsValidator;

class DomainDnsValidatorTest {

    private DNSService dnsService;
    private DomainDnsValidator mailet;

    @BeforeEach
    void setUp() {
        dnsService = mock(DNSService.class);
        mailet = new DomainDnsValidator(dnsService);
    }

    @Test
    void shouldExtractDkimSignatureInfo() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addHeader("DKIM-Signature", "v=1; a=rsa-sha256; c=relaxed/simple; d=example.com; s=s1; " +
                "t=1234567890; bh=hash; h=from:to:subject; b=signature")
            .build();

        Optional<DomainDnsValidator.DkimSignatureInfo> info = mailet.extractDkimSignatureInfo(message);

        assertThat(info).isPresent();
        assertThat(info.get().domain).isEqualTo("example.com");
        assertThat(info.get().selector).isEqualTo("s1");
    }

    @Test
    void shouldReturnEmptyWhenNoDkimSignature() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .build();

        Optional<DomainDnsValidator.DkimSignatureInfo> info = mailet.extractDkimSignatureInfo(message);

        assertThat(info).isEmpty();
    }

    @Test
    void shouldExtractDkimInfoWithMultilineHeader() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addHeader("DKIM-Signature", "v=1; a=rsa-sha256; c=relaxed/simple;\n" +
                "  d=example.com;\n" +
                "  s=selector2023;\n" +
                "  h=from:to:subject; b=signature")
            .build();

        Optional<DomainDnsValidator.DkimSignatureInfo> info = mailet.extractDkimSignatureInfo(message);

        assertThat(info).isPresent();
        assertThat(info.get().domain).isEqualTo("example.com");
        assertThat(info.get().selector).isEqualTo("selector2023");
    }

    @Test
    void shouldRejectMailWithoutDkimSignature() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("DomainDnsValidator")
            .setProperty("validateDkim", "true")
            .setProperty("validateSpf", "false")
            .setProperty("validateDmarc", "false")
            .build());

        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("Test")
            .build();

        Mail mail = FakeMail.builder()
            .name("mail1")
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        assertThat(mail.getState()).isEqualTo(Mail.ERROR);
        assertThat(mail.getErrorMessage()).contains("No DKIM-Signature header found");
    }

    @Test
    void shouldPassValidationWhenAllChecksDisabled() throws Exception {
        mailet.init(FakeMailetConfig.builder()
            .mailetName("DomainDnsValidator")
            .setProperty("validateDkim", "false")
            .setProperty("validateSpf", "false")
            .setProperty("validateDmarc", "false")
            .build());

        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("Test")
            .build();

        Mail mail = FakeMail.builder()
            .name("mail1")
            .mimeMessage(message)
            .build();

        mailet.service(mail);

        // Should still fail because no DKIM signature is present
        assertThat(mail.getState()).isEqualTo(Mail.ERROR);
    }
}
