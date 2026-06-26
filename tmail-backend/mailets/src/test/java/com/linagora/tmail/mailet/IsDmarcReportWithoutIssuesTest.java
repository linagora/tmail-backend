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

class IsDmarcReportWithoutIssuesTest extends DmarcReportMatcherTestBase {
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

    private static final String REPORT_ALL_RECORDS_PASS = """
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
              <count>3</count>
              <policy_evaluated>
                <disposition>none</disposition>
                <dkim>fail</dkim>
                <spf>pass</spf>
              </policy_evaluated>
            </row>
          </record>
        </feedback>
        """;

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

    private IsDmarcReportWithoutIssues matcher;

    @BeforeEach
    void setUp() throws Exception {
        matcher = new IsDmarcReportWithoutIssues();
        matcher.init(FakeMatcherConfig.builder()
            .matcherName("IsDmarcReportWithoutIssues")
            .build());
    }

    @Test
    void shouldMatchWhenReportHasNoIssues() throws Exception {
        Collection<MailAddress> result = matcher.match(mailWithZipAttachment(DMARC_SUBJECT, REPORT_WITHOUT_ISSUES));

        assertThat(result).containsOnly(RECIPIENT);
    }

    @Test
    void shouldMatchWhenAllRecordsPassWithAtLeastOneDkimOrSpf() throws Exception {
        Collection<MailAddress> result = matcher.match(mailWithZipAttachment(DMARC_SUBJECT, REPORT_ALL_RECORDS_PASS));

        assertThat(result).containsOnly(RECIPIENT);
    }

    @Test
    void shouldNotMatchWhenReportHasIssues() throws Exception {
        Collection<MailAddress> result = matcher.match(mailWithZipAttachment(DMARC_SUBJECT, REPORT_WITH_ISSUES));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchWhenMixedRecordsContainAtLeastOneFailing() throws Exception {
        Collection<MailAddress> result = matcher.match(mailWithZipAttachment(DMARC_SUBJECT, REPORT_MIXED_RECORDS));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchWhenSubjectDoesNotMatchDmarcPattern() throws Exception {
        Collection<MailAddress> result = matcher.match(mailWithZipAttachment("Random subject", REPORT_WITHOUT_ISSUES));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotMatchWhenMalformedXml() throws Exception {
        Collection<MailAddress> result = matcher.match(mailWithZipAttachment(DMARC_SUBJECT, "not xml"));

        assertThat(result).isEmpty();
    }
}
