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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;

/**
 * Validates DKIM DNS records for a given domain and selector.
 *
 * <p>
 * Checks that the DKIM public key record exists at selector._domainkey.domain
 * and contains a valid DKIM record (v=DKIM1). Checks that it is associated
 * with a DKIM key we accept.
 * </p>
 */
public class DkimDnsValidator {
    private final DNSService dnsService;
    private final List<String> acceptedDkimKeys;

    public DkimDnsValidator(DNSService dnsService, List<String> acceptedDkimKeys) {
        this.dnsService = dnsService;
        this.acceptedDkimKeys = acceptedDkimKeys;
    }

    /**
     * Validates the DKIM DNS record for the given domain and selector.
     *
     * @param domain the domain to validate
     * @param selector the DKIM selector
     * @return Optional validation failure if validation fails, empty if validation succeeds
     */
    public Optional<DnsValidationFailure.DkimValidationFailure> validate(Domain domain, String selector) {
        String dkimRecordName = buildDkimRecordName(selector, domain);

        try {
            Collection<String> txtRecords = dnsService.findTXTRecords(dkimRecordName);

            if (txtRecords == null || txtRecords.isEmpty()) {
                return Optional.of(new DnsValidationFailure.DkimValidationFailure(
                    String.format("No DKIM record found at %s", dkimRecordName)));
            }

            // Check if at least one record is a valid DKIM record
            boolean hasValidDkimRecord = txtRecords.stream()
                .filter(this::isValidDkimRecord)
                .anyMatch(this::hasDkimPublicKey);

            if (!hasValidDkimRecord) {
                return Optional.of(new DnsValidationFailure.DkimValidationFailure(
                    String.format("DKIM record at %s does not contain valid DKIM signature (must start with v=DKIM1 and have a valid public key)",
                        dkimRecordName)));
            }

            return Optional.empty();

        } catch (Exception e) {
            return Optional.of(new DnsValidationFailure.DkimValidationFailure(
                String.format("Failed to query DKIM record at %s: %s", dkimRecordName, e.getMessage())));
        }
    }

    @VisibleForTesting
    String buildDkimRecordName(String selector, Domain domain) {
        return selector + "._domainkey." + domain.asString();
    }

    @VisibleForTesting
    boolean isValidDkimRecord(String txtRecord) {
        // Use regex to allow flexible whitespace around version tag
        // Matches: "v=DKIM1", "v = DKIM1", " v=DKIM1 ", etc.
        return txtRecord.trim().matches("(?i).*v\\s*=\\s*DKIM1.*");
    }

    @VisibleForTesting
    boolean hasDkimPublicKey(String txtRecord) {
        // Handle multi-string DNS records by removing quotes, then parse DKIM tags
        String normalized = txtRecord.replace("\"", "");

        return Splitter.on(';').splitToStream(normalized)
            .map(String::trim)
            .map(s -> s.replace(" ", ""))
            .flatMap(tag -> {
                int eqIdx = tag.indexOf('=');
                if (eqIdx <=0 || eqIdx == tag.length() - 1) {
                    // Remove invalid tags
                    return Stream.empty();
                }
                if (!tag.substring(0, eqIdx).equalsIgnoreCase("p")) {
                    // Discard tags other than 'p'
                    return Stream.empty();
                }
                return Stream.of(tag.substring(eqIdx + 1));
            })
            .anyMatch(acceptedDkimKeys::contains);
    }
}
