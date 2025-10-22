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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.linagora.tmail.mailet.dns.DkimDnsValidator;
import com.linagora.tmail.mailet.dns.DmarcDnsValidator;
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
 *   <li><b>DKIM</b>: Verifies that the selector used in DKIM-Signature header has a valid DNS record</li>
 *   <li><b>SPF</b>: Ensures the domain's SPF record authorizes the configured server IPs</li>
 *   <li><b>DMARC</b>: Validates that a DMARC policy exists with minimum quarantine level</li>
 * </ul>
 * </p>
 *
 * <p>
 * Configuration parameters:
 * <ul>
 *   <li><b>validateDkim</b>: Enable DKIM validation (default: true)</li>
 *   <li><b>validateSpf</b>: Enable SPF validation (default: true)</li>
 *   <li><b>validateDmarc</b>: Enable DMARC validation (default: true)</li>
 *   <li><b>spfAuthorizedIps</b>: Comma-separated list of IPs that must be authorized in SPF (e.g., "192.0.2.10,198.51.100.5")</li>
 *   <li><b>dmarcMinPolicy</b>: Minimum DMARC policy required (quarantine or reject, default: quarantine)</li>
 * </ul>
 * </p>
 *
 * <p>
 * Example configuration:
 * </p>
 * <pre>
 * {@code
 * <mailet match="All" class="com.linagora.tmail.mailet.DomainDnsValidator">
 *   <validateDkim>true</validateDkim>
 *   <validateSpf>true</validateSpf>
 *   <validateDmarc>true</validateDmarc>
 *   <spfAuthorizedIps>192.0.2.10,198.51.100.5</spfAuthorizedIps>
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

    private static final String VALIDATE_DKIM_PARAM = "validateDkim";
    private static final String VALIDATE_SPF_PARAM = "validateSpf";
    private static final String VALIDATE_DMARC_PARAM = "validateDmarc";
    private static final String SPF_AUTHORIZED_IPS_PARAM = "spfAuthorizedIps";
    private static final String DMARC_MIN_POLICY_PARAM = "dmarcMinPolicy";

    private static final String DKIM_SIGNATURE_HEADER = "DKIM-Signature";
    private static final Pattern DKIM_SELECTOR_PATTERN = Pattern.compile("s=([^;\\s]+)");
    private static final Pattern DKIM_DOMAIN_PATTERN = Pattern.compile("d=([^;\\s]+)");

    private final DNSService dnsService;
    private boolean validateDkim;
    private boolean validateSpf;
    private boolean validateDmarc;
    private SpfDnsValidator spfValidator;
    private DkimDnsValidator dkimValidator;
    private DmarcDnsValidator dmarcValidator;

    @Inject
    public DomainDnsValidator(DNSService dnsService) {
        this.dnsService = dnsService;
    }

    @Override
    public void init() throws MessagingException {
        validateDkim = Boolean.parseBoolean(getInitParameter(VALIDATE_DKIM_PARAM, "true"));
        validateSpf = Boolean.parseBoolean(getInitParameter(VALIDATE_SPF_PARAM, "true"));
        validateDmarc = Boolean.parseBoolean(getInitParameter(VALIDATE_DMARC_PARAM, "true"));

        if (validateSpf) {
            String spfAuthorizedIps = getInitParameter(SPF_AUTHORIZED_IPS_PARAM);
            if (Strings.isNullOrEmpty(spfAuthorizedIps)) {
                throw new MessagingException("spfAuthorizedIps parameter is required when validateSpf is enabled");
            }
            spfValidator = new SpfDnsValidator(dnsService, spfAuthorizedIps);
        }

        if (validateDkim) {
            dkimValidator = new DkimDnsValidator(dnsService);
        }

        if (validateDmarc) {
            String dmarcMinPolicy = getInitParameter(DMARC_MIN_POLICY_PARAM, "quarantine");
            dmarcValidator = new DmarcDnsValidator(dnsService, dmarcMinPolicy);
        }

        LOGGER.info("DomainDnsValidator initialized - validateDkim: {}, validateSpf: {}, validateDmarc: {}",
            validateDkim, validateSpf, validateDmarc);
    }

    @Override
    public String getMailetInfo() {
        return "Domain DNS Validator Mailet";
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        try {
            MimeMessage message = mail.getMessage();

            // Extract DKIM signature information
            Optional<DkimSignatureInfo> dkimInfo = extractDkimSignatureInfo(message);

            if (!dkimInfo.isPresent()) {
                String errorMsg = "No DKIM-Signature header found. Email must be DKIM signed before validation.";
                LOGGER.warn("Mail {} rejected: {}", mail.getName(), errorMsg);
                mail.setErrorMessage(errorMsg);
                mail.setState(Mail.ERROR);
                return;
            }

            String domain = dkimInfo.get().domain;
            String selector = dkimInfo.get().selector;

            LOGGER.debug("Validating DNS records for domain: {}, selector: {}", domain, selector);

            // Validate DKIM DNS record
            if (validateDkim) {
                Optional<String> dkimError = dkimValidator.validate(domain, selector);
                if (dkimError.isPresent()) {
                    handleValidationFailure(mail, "DKIM", dkimError.get());
                    return;
                }
            }

            // Validate SPF record
            if (validateSpf) {
                Optional<String> spfError = spfValidator.validate(domain);
                if (spfError.isPresent()) {
                    handleValidationFailure(mail, "SPF", spfError.get());
                    return;
                }
            }

            // Validate DMARC record
            if (validateDmarc) {
                Optional<String> dmarcError = dmarcValidator.validate(domain);
                if (dmarcError.isPresent()) {
                    handleValidationFailure(mail, "DMARC", dmarcError.get());
                    return;
                }
            }

            LOGGER.info("DNS validation passed for domain: {}", domain);

        } catch (Exception e) {
            LOGGER.error("Error during DNS validation for mail {}", mail.getName(), e);
            mail.setErrorMessage("DNS validation failed: " + e.getMessage());
            mail.setState(Mail.ERROR);
        }
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
        mail.setState(Mail.ERROR);
    }

    @VisibleForTesting
    static class DkimSignatureInfo {
        final String domain;
        final String selector;

        DkimSignatureInfo(String domain, String selector) {
            this.domain = domain;
            this.selector = selector;
        }
    }
}
