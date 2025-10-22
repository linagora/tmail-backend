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

import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.james.dnsservice.api.DNSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * Validates DMARC DNS records for a given domain.
 *
 * <p>
 * Checks that the domain has a DMARC record at _dmarc.domain with a minimum
 * policy level (quarantine or reject).
 * </p>
 */
public class DmarcDnsValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DmarcDnsValidator.class);
    private static final String DMARC_PREFIX = "v=DMARC1";
    private static final String DMARC_SUBDOMAIN = "_dmarc.";
    private static final Pattern DMARC_POLICY_PATTERN = Pattern.compile("p=([^;\\s]+)");

    public enum DmarcPolicy {
        NONE(0),
        QUARANTINE(1),
        REJECT(2);

        private final int level;

        DmarcPolicy(int level) {
            this.level = level;
        }

        public static Optional<DmarcPolicy> fromString(String policy) {
            try {
                return Optional.of(DmarcPolicy.valueOf(policy.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        public boolean isStricterOrEqualTo(DmarcPolicy other) {
            return this.level >= other.level;
        }
    }

    private final DNSService dnsService;
    private final DmarcPolicy minimumPolicy;

    public DmarcDnsValidator(DNSService dnsService, String minimumPolicyStr) {
        this.dnsService = dnsService;
        this.minimumPolicy = DmarcPolicy.fromString(minimumPolicyStr)
            .orElseThrow(() -> new IllegalArgumentException(
                "Invalid DMARC policy: " + minimumPolicyStr + ". Must be one of: none, quarantine, reject"));
    }

    /**
     * Validates the DMARC DNS record for the given domain.
     *
     * @param domain the domain to validate
     * @return Optional error message if validation fails, empty if validation succeeds
     */
    public Optional<String> validate(String domain) {
        String dmarcRecordName = buildDmarcRecordName(domain);

        try {
            Collection<String> txtRecords = dnsService.findTXTRecords(dmarcRecordName);

            if (txtRecords == null || txtRecords.isEmpty()) {
                String error = String.format("No DMARC record found at %s", dmarcRecordName);
                LOGGER.warn(error);
                return Optional.of(error);
            }

            // Find DMARC record
            Optional<String> dmarcRecord = txtRecords.stream()
                .filter(this::isDmarcRecord)
                .findFirst();

            if (!dmarcRecord.isPresent()) {
                String error = String.format("No valid DMARC record found at %s (must start with v=DMARC1)", dmarcRecordName);
                LOGGER.warn(error);
                return Optional.of(error);
            }

            // Extract and validate policy
            Optional<DmarcPolicy> policy = extractPolicy(dmarcRecord.get());

            if (!policy.isPresent()) {
                String error = String.format("DMARC record at %s does not contain a valid policy (p=). Record: %s",
                    dmarcRecordName, dmarcRecord.get());
                LOGGER.warn(error);
                return Optional.of(error);
            }

            if (!policy.get().isStricterOrEqualTo(minimumPolicy)) {
                String error = String.format("DMARC policy for domain %s is too lenient. Required: %s, Found: %s. Record: %s",
                    domain, minimumPolicy.name().toLowerCase(), policy.get().name().toLowerCase(), dmarcRecord.get());
                LOGGER.warn(error);
                return Optional.of(error);
            }

            LOGGER.debug("DMARC validation passed for domain {} with policy {}", domain, policy.get());
            return Optional.empty();

        } catch (Exception e) {
            String error = String.format("Failed to query DMARC record at %s: %s", dmarcRecordName, e.getMessage());
            LOGGER.error(error, e);
            return Optional.of(error);
        }
    }

    @VisibleForTesting
    String buildDmarcRecordName(String domain) {
        return DMARC_SUBDOMAIN + domain;
    }

    @VisibleForTesting
    boolean isDmarcRecord(String txtRecord) {
        // Use regex to allow flexible whitespace around version tag
        // Matches: "v=DMARC1", "v = DMARC1", " v=DMARC1 ", etc.
        return txtRecord.trim().matches("(?i)^v\\s*=\\s*DMARC1.*");
    }

    @VisibleForTesting
    Optional<DmarcPolicy> extractPolicy(String dmarcRecord) {
        Matcher matcher = DMARC_POLICY_PATTERN.matcher(dmarcRecord);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String policyValue = matcher.group(1);
        return DmarcPolicy.fromString(policyValue);
    }
}
