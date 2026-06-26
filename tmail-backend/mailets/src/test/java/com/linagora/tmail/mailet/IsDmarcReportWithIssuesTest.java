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

import java.util.Collection;

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IsDmarcReportWithIssuesTest extends DmarcReportMatcherTestBase {
    private static final String REPORT_WITH_ISSUES = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feedback>
          <record>
            <row>
              <source_ip>192.0.2.1</source_ip>
              <count>1</count>
              <policy_evaluated>
                <disposition>quarantine</disposition>
                <dkim>fail</dkim>
                <spf>fail</spf>
              </policy_evaluated>
            </row>
          </record>
        </feedback>
        """;

    private static final String REPORT_WITHOUT_ISSUES = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feedback>
          <record>
            <row>
              <source_ip>192.0.2.1</source_ip>
              <count>5</count>
              <policy_evaluated>
                <disposition>none</disposition>
                <dkim>pass</dkim>
                <spf>fail</spf>
              </policy_evaluated>
            </row>
          </record>
        </feedback>
        """;

    private static final String REPORT_MIXED_RECORDS = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feedback>
          <record>
            <row>
              <source_ip>192.0.2.1</source_ip>
              <count>5</count>
              <policy_evaluated>
                <disposition>none</disposition>
                <dkim>pass</dkim>
                <spf>fail</spf>
              </policy_evaluated>
            </row>
          </record>
          <record>
            <row>
              <source_ip>198.51.100.1</source_ip>
              <count>2</count>
              <policy_evaluated>
                <disposition>quarantine</disposition>
                <dkim>fail</dkim>
                <spf>fail</spf>
              </policy_evaluated>
            </row>
          </record>
        </feedback>
        """;

    private static final String REPORT_DISPOSITION_QUARANTINE_BUT_DKIM_PASS = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feedback>
          <record>
            <row>
              <source_ip>192.0.2.1</source_ip>
              <count>3</count>
              <policy_evaluated>
                <disposition>quarantine</disposition>
                <dkim>pass</dkim>
                <spf>fail</spf>
              </policy_evaluated>
            </row>
          </record>
        </feedback>
        """;

    private IsDmarcReportWithIssues matcher;

    @BeforeEach
    void setUp() throws Exception {
        matcher = new IsDmarcReportWithIssues();
        matcher.init(FakeMatcherConfig.builder()
            .matcherName("IsDmarcReportWithIssues")
            .build());
    }

    @Test
    void shouldMatchWhenReportHasIssuesInZip() throws Exception {
        Collection<MailAddress> result = matcher.match(mailWithZipAttachment(DMARC_SUBJECT, REPORT_WITH_ISSUES));

        assertThat(result).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenReportHasIssuesInGzip() throws Exception {
        Collection<MailAddress> result = matcher.match(mailWithGzipAttachment(DMARC_SUBJECT, REPORT_WITH_ISSUES));

        assertThat(result).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenReportHasIssuesInPlainXml() throws Exception {
        Collection<MailAddress> result = matcher.match(mailWithXmlAttachment(DMARC_SUBJECT, REPORT_WITH_ISSUES));

        assertThat(result).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenMixedRecordsContainAtLeastOneFailing() throws Exception {
        Collection<MailAddress> result = matcher.match(mailWithZipAttachment(DMARC_SUBJECT, REPORT_MIXED_RECORDS));

        assertThat(result).containsOnly(RECIPIENT);
    }

    @Test
    void shouldNotMatchWhenReportHasNoIssues() throws Exception {
        Collection<MailAddress> result = matcher.match(mailWithZipAttachment(DMARC_SUBJECT, REPORT_WITHOUT_ISSUES));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchWhenDispositionIsQuarantineButDkimPasses() throws Exception {
        Collection<MailAddress> result = matcher.match(
            mailWithZipAttachment(DMARC_SUBJECT, REPORT_DISPOSITION_QUARANTINE_BUT_DKIM_PASS));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchWhenSubjectDoesNotMatchDmarcPattern() throws Exception {
        Collection<MailAddress> result = matcher.match(mailWithZipAttachment("Regular email subject", REPORT_WITH_ISSUES));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchWhenMalformedXml() throws Exception {
        String malformed = "<this is not valid xml";
        Collection<MailAddress> result = matcher.match(mailWithZipAttachment(DMARC_SUBJECT, malformed));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchWhenXmlContainsDoctypeDeclaration() throws Exception {
        String withDoctype = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE feedback [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <feedback>
              <record>
                <row>
                  <policy_evaluated>
                    <dkim>fail</dkim>
                    <spf>fail</spf>
                  </policy_evaluated>
                </row>
              </record>
            </feedback>
            """;
        Collection<MailAddress> result = matcher.match(mailWithZipAttachment(DMARC_SUBJECT, withDoctype));

        assertThat(result).isEmpty();
    }
}
