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
import java.util.regex.Pattern;

import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;

import com.google.common.annotations.VisibleForTesting;

/**
 * Validates SPF DNS records for a given domain.
 *
 * <p>
 * Checks that the domain's SPF record includes the TMail SPF configuration
 * via an include mechanism (e.g., "include:_spf.tmail.com").
 * </p>
 */
public class SpfDnsValidator {
    private static final String SPF_PREFIX = "v=spf1";

    private final DNSService dnsService;
    private final String requiredSpfInclude;
    private final Pattern includePattern;

    public SpfDnsValidator(DNSService dnsService, String requiredSpfInclude) {
        this.dnsService = dnsService;
        this.requiredSpfInclude = requiredSpfInclude;
        // Pattern to match "include:domain" in SPF record, case-insensitive
        this.includePattern = Pattern.compile("(?i)\\binclude:" + Pattern.quote(requiredSpfInclude) + "\\b");
    }

    /**
     * Validates the SPF DNS record for the given domain.
     *
     * @param domain the domain to validate
     * @return Optional validation failure if validation fails, empty if validation succeeds
     */
    public Optional<DnsValidationFailure.SpfValidationFailure> validate(Domain domain) {
        try {
            Collection<String> txtRecords = dnsService.findTXTRecords(domain.asString());

            if (txtRecords == null || txtRecords.isEmpty()) {
                return Optional.of(new DnsValidationFailure.SpfValidationFailure(
                    String.format("No TXT records found for domain %s", domain)));
            }

            // Find SPF record
            Optional<String> spfRecord = txtRecords.stream()
                .filter(this::isSpfRecord)
                .findFirst();

            if (!spfRecord.isPresent()) {
                return Optional.of(new DnsValidationFailure.SpfValidationFailure(
                    String.format("No SPF record found for domain %s", domain)));
            }

            // Check if SPF includes TMail's SPF configuration
            if (!includesRequiredSpf(spfRecord.get())) {
                return Optional.of(new DnsValidationFailure.SpfValidationFailure(
                    String.format("SPF record for domain %s does not include required SPF configuration 'include:%s'. Current SPF: %s",
                        domain, requiredSpfInclude, spfRecord.get())));
            }

            return Optional.empty();

        } catch (Exception e) {
            return Optional.of(new DnsValidationFailure.SpfValidationFailure(
                String.format("Failed to query SPF record for domain %s: %s", domain, e.getMessage())));
        }
    }

    @VisibleForTesting
    boolean isSpfRecord(String txtRecord) {
        String normalized = unquote(txtRecord.trim());
        return normalized.startsWith(SPF_PREFIX);
    }

    private static String unquote(String txtRecord) {
        if (txtRecord.startsWith("\"")) {
            return unquote(txtRecord.substring(1).trim());
        }
        if (txtRecord.endsWith("\"")) {
            return unquote(txtRecord.substring(0, txtRecord.length() - 1).trim());
        }
        return txtRecord;
    }

    @VisibleForTesting
    boolean includesRequiredSpf(String spfRecord) {
        return includePattern.matcher(spfRecord).find();
    }
}
