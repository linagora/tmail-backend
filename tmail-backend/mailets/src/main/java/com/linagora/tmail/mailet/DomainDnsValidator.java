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

import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.Domain;
import org.apache.james.core.MaybeSender;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.mailet.Mail;
import org.apache.mailet.ProcessingState;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.mailet.dns.DkimDnsValidator;
import com.linagora.tmail.mailet.dns.DmarcDnsValidator;
import com.linagora.tmail.mailet.dns.DnsValidationFailure;
import com.linagora.tmail.mailet.dns.SpfDnsValidator;

/**
 * <p>
 * Validates DNS records (SPF, DKIM, DMARC) for outbound emails to ensure proper domain configuration
 * before sending. This mailet is designed for SaaS environments where customers send emails through
 * your infrastructure.
 * </p>
 *
 * <p>
 * The validation occurs after DKIM signing and checks:
 * <ul>
 *   <li><b>DKIM</b>: Verifies that the selector used in DKIM-Signature header has a valid DNS record with one of the accepted public key.</li>
 *   <li><b>SPF</b>: Ensures the domain's SPF record includes TMail's SPF configuration via include mechanism</li>
 *   <li><b>DMARC</b>: Validates that a DMARC policy exists with minimum quarantine level</li>
 * </ul>
 * </p>
 *
 * <p>
 * Configuration parameters:
 * <ul>
 *   <li><b>acceptedDkimKeys</b>: DKIM keys we consider as accepted for our service.</li>
 *   <li><b>spfInclude</b>: SPF include domain that must be present in customer's SPF (e.g., "_spf.tmail.com")</li>
 *   <li><b>dmarcMinPolicy</b>: Minimum DMARC policy required (quarantine or reject, default: quarantine)</li>
 *   <li><b>validationFailureProcessor</b>: Processor to send invalid emails to.</li>
 * </ul>
 * </p>
 *
 * <p>
 * Example configuration:
 * </p>
 * <pre>
 * {@code
 * <mailet match="All" class="com.linagora.tmail.mailet.DomainDnsValidator">
 *   <spfInclude>_spf.tmail.com</spfInclude>
 *   <acceptedDkimKeys>MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwc8ksmQvN3Qk8rOehLdu4xTzBz5E9WjX3V8fK9zLutw4F5mvh0qFQeX1nVqYQ1oHXv8z9pHvh2cWqg8zP/0DPW8nG+9PRcZ8mDFeG9Oa2CE8vNQXGvG+y9bS5QIDAQAB</acceptedDkimKeys>
 *   <dmarcMinPolicy>quarantine</dmarcMinPolicy>
 * </mailet>
 * }
 * </pre>
 *
 * <p>
 * This mailet should be placed in the transport processor, after DKIMSign and before RemoteDelivery.
 * If validation fails, the mail is bounced with an appropriate error message.
 * </p>
 */
public class DomainDnsValidator extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(DomainDnsValidator.class);

    @VisibleForTesting
    record DkimSignatureInfo(String domain, String selector) {

    }

    private static final String SPF_INCLUDE_PARAM = "spfInclude";
    private static final String DKIM_KEY_PARAM = "acceptedDkimKeys";
    private static final String DMARC_MIN_POLICY_PARAM = "dmarcMinPolicy";

    private static final String DKIM_SIGNATURE_HEADER = "DKIM-Signature";
    private static final Pattern DKIM_SELECTOR_PATTERN = Pattern.compile("s=([^;\\s]+)");
    private static final Pattern DKIM_DOMAIN_PATTERN = Pattern.compile("d=([^;\\s]+)");

    private final DNSService dnsService;
    private SpfDnsValidator spfValidator;
    private DkimDnsValidator dkimValidator;
    private DmarcDnsValidator dmarcValidator;
    private String validationFailureProcessor = Mail.ERROR;

    @Inject
    public DomainDnsValidator(DNSService dnsService) {
        this.dnsService = dnsService;
    }

    @Override
    public void init() throws MessagingException {
        String spfInclude = getInitParameter(SPF_INCLUDE_PARAM);
        if (!Strings.isNullOrEmpty(spfInclude)) {
            spfValidator = new SpfDnsValidator(dnsService, spfInclude);
        }

        String dkimKeys = getInitParameter(DKIM_KEY_PARAM);
        if (!Strings.isNullOrEmpty(dkimKeys)) {
            dkimValidator = new DkimDnsValidator(dnsService,
                Splitter.on(',')
                    .splitToList(dkimKeys));
        }

        String dmarcMinPolicy = getInitParameter(DMARC_MIN_POLICY_PARAM);
        if (!Strings.isNullOrEmpty(dmarcMinPolicy)) {
            dmarcValidator = new DmarcDnsValidator(dnsService, dmarcMinPolicy);
        }

        validationFailureProcessor = getInitParameter("validationFailureProcessor", Mail.ERROR);
    }

    @Override
    public String getMailetInfo() {
        return "Domain DNS Validator Mailet";
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();
        MaybeSender maybeSender = mail.getMaybeSender();

        if (maybeSender.isNullSender()) {
            LOGGER.debug("Skip DNS validation for {} has it is sent by <> sender", mail.getName());
            return;
        }
        Domain domain = maybeSender.get().getDomain();

        // Validate DKIM DNS record
        if (dkimValidator != null) {
            // Extract DKIM signature information
            Optional<DkimSignatureInfo> dkimInfo = extractDkimSignatureInfo(message);
            if (dkimInfo.isEmpty()) {
                String errorMsg = "No DKIM-Signature header found. Email must be DKIM signed before validation.";
                LOGGER.warn("Mail {} rejected: {}", mail.getName(), errorMsg);
                mail.setErrorMessage(errorMsg);
                mail.setState(Mail.ERROR);
                return;
            }
            String selector = dkimInfo.get().selector;
            Optional<DnsValidationFailure.DkimValidationFailure> dkimError = dkimValidator.validate(domain, selector);
            LOGGER.debug("Validating DNS records for domain: {}, selector: {}", domain, selector);
            if (dkimError.isPresent()) {
                handleValidationFailure(mail, "DKIM", dkimError.get().message());
                return;
            }
        }

        // Validate SPF record
        if (spfValidator != null) {
            Optional<DnsValidationFailure.SpfValidationFailure> spfError = spfValidator.validate(domain);
            if (spfError.isPresent()) {
                handleValidationFailure(mail, "SPF", spfError.get().message());
                return;
            }
        }

        // Validate DMARC record
        if (dmarcValidator != null) {
            Optional<DnsValidationFailure.DmarcValidationFailure> dmarcError = dmarcValidator.validate(domain);
            if (dmarcError.isPresent()) {
                handleValidationFailure(mail, "DMARC", dmarcError.get().message());
                return;
            }
        }

        LOGGER.debug("DNS validation passed for domain: {}", domain);
    }

    @VisibleForTesting
    Optional<DkimSignatureInfo> extractDkimSignatureInfo(MimeMessage message) throws MessagingException {
        String[] dkimHeaders = message.getHeader(DKIM_SIGNATURE_HEADER);
        if (dkimHeaders == null || dkimHeaders.length == 0) {
            return Optional.empty();
        }

        // Use the first DKIM-Signature header
        String dkimSignature = dkimHeaders[0];

        // Extract selector (s=)
        Matcher selectorMatcher = DKIM_SELECTOR_PATTERN.matcher(dkimSignature);
        if (!selectorMatcher.find()) {
            LOGGER.warn("Could not extract selector from DKIM-Signature header");
            return Optional.empty();
        }
        String selector = selectorMatcher.group(1);

        // Extract domain (d=)
        Matcher domainMatcher = DKIM_DOMAIN_PATTERN.matcher(dkimSignature);
        if (!domainMatcher.find()) {
            LOGGER.warn("Could not extract domain from DKIM-Signature header");
            return Optional.empty();
        }
        String domain = domainMatcher.group(1);

        return Optional.of(new DkimSignatureInfo(domain, selector));
    }

    private void handleValidationFailure(Mail mail, String recordType, String errorMessage) {
        String fullError = String.format("%s validation failed: %s", recordType, errorMessage);
        LOGGER.warn("Mail {} rejected: {}", mail.getName(), fullError);
        mail.setErrorMessage(fullError);
        mail.setState(validationFailureProcessor);
    }

    @Override
    public Collection<ProcessingState> requiredProcessingState() {
        return ImmutableList.of(new ProcessingState(validationFailureProcessor));
    }
}
