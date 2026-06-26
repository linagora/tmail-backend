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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;
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

abstract class DmarcReportMatcherTestBase {
    protected static final MailAddress RECIPIENT = newAddress("recipient@example.com");
    protected static final String DMARC_SUBJECT =
        "Report Domain: example.com Submitter: google.com Report-ID: 12345";

    record Attachment(byte[] bytes, String contentType, String filename) {}

    protected FakeMail mailWithZipAttachment(String subject, String xmlContent) throws Exception {
        byte[] zipBytes = createZip("report.xml", xmlContent.getBytes(StandardCharsets.UTF_8));
        return mailWithAttachment(subject, new Attachment(zipBytes, "application/zip",
            "example.com!google.com!1749340800!1749427199.zip"));
    }

    protected FakeMail mailWithGzipAttachment(String subject, String xmlContent) throws Exception {
        byte[] gzipBytes = createGzip(xmlContent.getBytes(StandardCharsets.UTF_8));
        return mailWithAttachment(subject, new Attachment(gzipBytes, "application/gzip",
            "example.com!google.com!1749340800!1749427199.xml.gz"));
    }

    protected FakeMail mailWithXmlAttachment(String subject, String xmlContent) throws Exception {
        return mailWithAttachment(subject, new Attachment(
            xmlContent.getBytes(StandardCharsets.UTF_8), "application/xml",
            "example.com!google.com!1749340800!1749427199.xml"));
    }

    private FakeMail mailWithAttachment(String subject, Attachment attachment) throws Exception {
        MimeMessage message = buildMultipartMessage(subject, attachment);
        return FakeMail.builder().name("test").recipient(RECIPIENT).mimeMessage(message).build();
    }

    private static MimeMessage buildMultipartMessage(String subject, Attachment attachment) throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage message = new MimeMessage(session);
        message.setSubject(subject);

        MimeMultipart multipart = new MimeMultipart("mixed");

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("DMARC aggregate report");
        multipart.addBodyPart(textPart);

        MimeBodyPart attachPart = new MimeBodyPart();
        attachPart.setDataHandler(new DataHandler(
            new ByteArrayDataSource(attachment.bytes(), attachment.contentType())));
        attachPart.setFileName(attachment.filename());
        multipart.addBodyPart(attachPart);

        message.setContent(multipart);
        message.saveChanges();

        // Write and re-read so parts are backed by DataSource, enabling getInputStream().
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

    private static byte[] createGzip(byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(content);
        }
        return baos.toByteArray();
    }

    protected static MailAddress newAddress(String address) {
        try {
            return new MailAddress(address);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
