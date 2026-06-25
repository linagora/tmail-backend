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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;

import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DmarcReportAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DmarcReportAnalyzer.class);

    private static final Pattern DMARC_SUBJECT_PATTERN = Pattern.compile(
        "(?i)Report\\s+Domain:[^\\r\\n]+Submitter:");

    private static final long MAX_UNCOMPRESSED_BYTES = 50L * 1024 * 1024;

    private static final XMLInputFactory XML_INPUT_FACTORY;

    static {
        XML_INPUT_FACTORY = XMLInputFactory.newFactory();
        XML_INPUT_FACTORY.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XML_INPUT_FACTORY.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    }

    /**
     * Analyzes a mail to determine if it is a DMARC aggregate report with authentication failures.
     *
     * @return {@code Optional.empty()} if the mail is not a DMARC report or cannot be parsed;
     *         {@code Optional.of(true)} if at least one record has both dkim=fail and spf=fail;
     *         {@code Optional.of(false)} if the report has no such failures.
     */
    static Optional<Boolean> analyze(Mail mail) throws MessagingException {
        String subject = mail.getMessage().getSubject();
        if (subject == null || !DMARC_SUBJECT_PATTERN.matcher(subject).find()) {
            return Optional.empty();
        }

        try {
            return findAndParseAttachment(mail.getMessage());
        } catch (Exception e) {
            LOGGER.warn("Failed to analyze DMARC report attachment", e);
            return Optional.empty();
        }
    }

    private static Optional<Boolean> findAndParseAttachment(MimeMessage message)
            throws MessagingException, IOException, XMLStreamException {
        if (!message.isMimeType("multipart/*")) {
            return Optional.empty();
        }
        return findAndParseInMultipart((Multipart) message.getContent());
    }

    private static Optional<Boolean> findAndParseInMultipart(Multipart multipart)
            throws MessagingException, IOException, XMLStreamException {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            Optional<Boolean> result = tryParsePart(part);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private static Optional<Boolean> tryParsePart(BodyPart part)
            throws MessagingException, IOException, XMLStreamException {
        String filename = Optional.ofNullable(part.getFileName()).orElse("").toLowerCase();
        String contentType = part.getContentType().split(";")[0].trim().toLowerCase();

        if (isZip(contentType, filename)) {
            try (InputStream inputStream = part.getInputStream()) {
                return parseZip(inputStream);
            }
        }
        if (isGzip(contentType, filename)) {
            try (InputStream inputStream = part.getInputStream()) {
                return parseGzip(inputStream);
            }
        }
        if (isXml(contentType, filename)) {
            try (InputStream inputStream = part.getInputStream()) {
                return parseXml(limitedStream(inputStream, MAX_UNCOMPRESSED_BYTES));
            }
        }

        Object content = part.getContent();
        if (content instanceof Multipart nestedMultipart) {
            return findAndParseInMultipart(nestedMultipart);
        }
        return Optional.empty();
    }

    private static boolean isZip(String contentType, String filename) {
        return contentType.equals("application/zip") || contentType.equals("application/x-zip") || filename.endsWith(".zip");
    }

    private static boolean isGzip(String contentType, String filename) {
        return contentType.equals("application/gzip") || contentType.equals("application/x-gzip") || filename.endsWith(".gz");
    }

    private static boolean isXml(String contentType, String filename) {
        return contentType.equals("application/xml") || contentType.equals("text/xml") || filename.endsWith(".xml");
    }

    private static Optional<Boolean> parseZip(InputStream rawInputStream) throws IOException, XMLStreamException {
        try (ZipInputStream zipInputStream = new ZipInputStream(rawInputStream)) {
            ZipEntry entry;
            boolean parsedXml = false;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName().toLowerCase(Locale.ROOT);
                if (!entry.isDirectory() && entryName.endsWith(".xml")) {
                    parsedXml = true;
                    if (parseXml(limitedStream(zipInputStream, MAX_UNCOMPRESSED_BYTES)).orElse(false)) {
                        return Optional.of(true);
                    }
                }
                zipInputStream.closeEntry();
            }
            if (!parsedXml) {
                LOGGER.warn("No XML entry found in DMARC ZIP attachment");
                return Optional.empty();
            }
            return Optional.of(false);
        }
    }

    private static Optional<Boolean> parseGzip(InputStream rawInputStream) throws IOException, XMLStreamException {
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(rawInputStream)) {
            return parseXml(limitedStream(gzipInputStream, MAX_UNCOMPRESSED_BYTES));
        }
    }

    private static Optional<Boolean> parseXml(InputStream xmlInputStream) throws XMLStreamException {
        XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(xmlInputStream);
        try {
            return scanForIssues(reader);
        } finally {
            reader.close();
        }
    }

    private static Optional<Boolean> scanForIssues(XMLStreamReader reader) throws XMLStreamException {
        RecordState state = new RecordState();
        while (reader.hasNext()) {
            int eventType = reader.next();
            if (eventType == XMLStreamConstants.DTD) {
                throw new XMLStreamException("DTD declarations are not allowed in DMARC reports");
            }
            if (dispatchXmlEvent(reader, state, eventType)) {
                return Optional.of(true);
            }
        }
        return state.seenRecord()
            ? Optional.of(false)
            : Optional.empty();
    }

    private static boolean dispatchXmlEvent(XMLStreamReader reader, RecordState state, int eventType) throws XMLStreamException {
        if (eventType == XMLStreamConstants.START_ELEMENT) {
            state.onStartElement(reader.getLocalName());
        } else if (eventType == XMLStreamConstants.CHARACTERS || eventType == XMLStreamConstants.CDATA) {
            state.onText(reader.getText());
        } else if (eventType == XMLStreamConstants.END_ELEMENT) {
            return state.onEndElement(reader.getLocalName());
        }
        return false;
    }

    private static boolean isFail(String value) {
        return value != null && "fail".equalsIgnoreCase(value.trim());
    }

    private static class RecordState {
        private boolean seenRecord = false;
        private boolean inRecord = false;
        private boolean inPolicyEvaluated = false;
        private String currentElement = null;
        private String dkimResult = null;
        private String spfResult = null;

        boolean seenRecord() {
            return seenRecord;
        }

        void onStartElement(String name) {
            if ("record".equals(name)) {
                seenRecord = true;
                inRecord = true;
                dkimResult = null;
                spfResult = null;
            } else if ("policy_evaluated".equals(name) && inRecord) {
                inPolicyEvaluated = true;
            } else if (inPolicyEvaluated) {
                currentElement = name;
            }
        }

        void onText(String text) {
            if (!inPolicyEvaluated || currentElement == null) {
                return;
            }
            if ("dkim".equals(currentElement)) {
                dkimResult = (dkimResult == null ? "" : dkimResult) + text;
            } else if ("spf".equals(currentElement)) {
                spfResult = (spfResult == null ? "" : spfResult) + text;
            }
        }

        boolean onEndElement(String name) {
            if ("policy_evaluated".equals(name)) {
                inPolicyEvaluated = false;
                currentElement = null;
            } else if ("record".equals(name)) {
                inRecord = false;
                boolean hasFailure = isFail(dkimResult) && isFail(spfResult);
                dkimResult = null;
                spfResult = null;
                return hasFailure;
            } else if (inPolicyEvaluated && name.equals(currentElement)) {
                currentElement = null;
            }
            return false;
        }
    }

    private static InputStream limitedStream(InputStream in, long maxBytes) {
        return new FilterInputStream(in) {
            private long bytesRead = 0;

            @Override
            public int read() throws IOException {
                int b = super.read();
                if (b != -1 && ++bytesRead > maxBytes) {
                    throw new IOException("DMARC attachment exceeds maximum allowed size of " + maxBytes + " bytes");
                }
                return b;
            }

            @Override
            public int read(byte[] buf, int off, int len) throws IOException {
                int n = super.read(buf, off, len);
                if (n > 0) {
                    bytesRead += n;
                    if (bytesRead > maxBytes) {
                        throw new IOException("DMARC attachment exceeds maximum allowed size of " + maxBytes + " bytes");
                    }
                }
                return n;
            }
        };
    }
}
