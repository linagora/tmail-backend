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

import org.apache.james.dnsservice.api.DNSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * Validates DKIM DNS records for a given domain and selector.
 *
 * <p>
 * Checks that the DKIM public key record exists at selector._domainkey.domain
 * and contains a valid DKIM record (v=DKIM1).
 * </p>
 */
public class DkimDnsValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DkimDnsValidator.class);
    private static final String DKIM_RECORD_PREFIX = "v=DKIM1";

    private final DNSService dnsService;

    public DkimDnsValidator(DNSService dnsService) {
        this.dnsService = dnsService;
    }

    /**
     * Validates the DKIM DNS record for the given domain and selector.
     *
     * @param domain the domain to validate
     * @param selector the DKIM selector
     * @return Optional error message if validation fails, empty if validation succeeds
     */
    public Optional<String> validate(String domain, String selector) {
        String dkimRecordName = buildDkimRecordName(selector, domain);

        try {
            Collection<String> txtRecords = dnsService.findTXTRecords(dkimRecordName);

            if (txtRecords == null || txtRecords.isEmpty()) {
                String error = String.format("No DKIM record found at %s", dkimRecordName);
                LOGGER.warn(error);
                return Optional.of(error);
            }

            // Check if at least one record is a valid DKIM record
            boolean hasValidDkimRecord = txtRecords.stream()
                .anyMatch(this::isValidDkimRecord);

            if (!hasValidDkimRecord) {
                String error = String.format("DKIM record at %s does not contain valid DKIM signature (must start with v=DKIM1)",
                    dkimRecordName);
                LOGGER.warn(error);
                return Optional.of(error);
            }

            LOGGER.debug("DKIM validation passed for {}", dkimRecordName);
            return Optional.empty();

        } catch (Exception e) {
            String error = String.format("Failed to query DKIM record at %s: %s", dkimRecordName, e.getMessage());
            LOGGER.error(error, e);
            return Optional.of(error);
        }
    }

    @VisibleForTesting
    String buildDkimRecordName(String selector, String domain) {
        return selector + "._domainkey." + domain;
    }

    @VisibleForTesting
    boolean isValidDkimRecord(String txtRecord) {
        // Use regex to allow flexible whitespace around version tag
        // Matches: "v=DKIM1", "v = DKIM1", " v=DKIM1 ", etc.
        return txtRecord.trim().matches("(?i)^v\\s*=\\s*DKIM1.*");
    }
}
