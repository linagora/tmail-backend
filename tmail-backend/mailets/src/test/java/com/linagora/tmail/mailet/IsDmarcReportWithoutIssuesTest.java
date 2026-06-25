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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jakarta.activation.DataHandler;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IsDmarcReportWithoutIssuesTest {
    private static final MailAddress RECIPIENT = newAddress("recipient@example.com");
    private static final String DMARC_SUBJECT = "Report Domain: example.com Submitter: google.com Report-ID: 12345";

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

    private FakeMail mailWithZipAttachment(String subject, String xmlContent) throws Exception {
        byte[] zipBytes = createZip("report.xml", xmlContent.getBytes(StandardCharsets.UTF_8));
        MimeMessage message = buildMultipartMessage(subject, zipBytes, "application/zip",
            "example.com!google.com!1749340800!1749427199.zip");
        return FakeMail.builder().name("test").recipient(RECIPIENT).mimeMessage(message).build();
    }

    private static MimeMessage buildMultipartMessage(String subject, byte[] attachmentBytes,
            String attachmentContentType, String filename) throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage message = new MimeMessage(session);
        message.setSubject(subject);

        MimeMultipart multipart = new MimeMultipart("mixed");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("DMARC aggregate report");
        multipart.addBodyPart(textPart);

        MimeBodyPart attachPart = new MimeBodyPart();
        attachPart.setDataHandler(new DataHandler(new ByteArrayDataSource(attachmentBytes, attachmentContentType)));
        attachPart.setFileName(filename);
        multipart.addBodyPart(attachPart);

        message.setContent(multipart);
        message.saveChanges();

        // Write and re-read so parts are backed by DataSource (not DataHandler+Object),
        // enabling getInputStream() without requiring a DCH for the attachment MIME type.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        message.writeTo(baos);
        return new MimeMessage(session, new ByteArrayInputStream(baos.toByteArray()));
    }

    private static byte[] createZip(String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private static MailAddress newAddress(String address) {
        try {
            return new MailAddress(address);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
