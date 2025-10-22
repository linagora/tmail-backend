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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.james.dnsservice.api.DNSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

/**
 * Validates SPF DNS records for a given domain.
 *
 * <p>
 * Checks that the domain's SPF record exists and contains the required authorized IPs
 * (the TMail server IPs). Additional IPs in the SPF record are tolerated.
 * </p>
 */
public class SpfDnsValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpfDnsValidator.class);
    private static final String SPF_PREFIX = "v=spf1";

    private final DNSService dnsService;
    private final Set<String> requiredIps;

    public SpfDnsValidator(DNSService dnsService, String spfAuthorizedIps) {
        this.dnsService = dnsService;
        this.requiredIps = parseRequiredIps(spfAuthorizedIps);
    }

    /**
     * Validates the SPF DNS record for the given domain.
     *
     * @param domain the domain to validate
     * @return Optional error message if validation fails, empty if validation succeeds
     */
    public Optional<String> validate(String domain) {
        try {
            Collection<String> txtRecords = dnsService.findTXTRecords(domain);

            if (txtRecords == null || txtRecords.isEmpty()) {
                String error = String.format("No TXT records found for domain %s", domain);
                LOGGER.warn(error);
                return Optional.of(error);
            }

            // Find SPF record
            Optional<String> spfRecord = txtRecords.stream()
                .filter(this::isSpfRecord)
                .findFirst();

            if (!spfRecord.isPresent()) {
                String error = String.format("No SPF record found for domain %s", domain);
                LOGGER.warn(error);
                return Optional.of(error);
            }

            // Extract IPs from SPF record
            Set<String> spfIps = extractIpsFromSpfRecord(spfRecord.get());

            // Check if all required IPs are present
            Set<String> missingIps = requiredIps.stream()
                .filter(requiredIp -> !isIpAuthorized(requiredIp, spfIps))
                .collect(Collectors.toSet());

            if (!missingIps.isEmpty()) {
                String error = String.format("SPF record for domain %s is missing required IPs: %s. Current SPF: %s",
                    domain, String.join(", ", missingIps), spfRecord.get());
                LOGGER.warn(error);
                return Optional.of(error);
            }

            LOGGER.debug("SPF validation passed for domain {}", domain);
            return Optional.empty();

        } catch (Exception e) {
            String error = String.format("Failed to query SPF record for domain %s: %s", domain, e.getMessage());
            LOGGER.error(error, e);
            return Optional.of(error);
        }
    }

    @VisibleForTesting
    Set<String> parseRequiredIps(String spfAuthorizedIps) {
        return Splitter.on(',')
            .trimResults()
            .omitEmptyStrings()
            .splitToStream(spfAuthorizedIps)
            .collect(ImmutableSet.toImmutableSet());
    }

    @VisibleForTesting
    boolean isSpfRecord(String txtRecord) {
        String normalized = txtRecord.trim();
        return normalized.startsWith(SPF_PREFIX);
    }

    @VisibleForTesting
    Set<String> extractIpsFromSpfRecord(String spfRecord) {
        // Extract ip4: and ip6: mechanisms from SPF record
        // Example: "v=spf1 ip4:192.0.2.0/24 ip4:198.51.100.5 include:_spf.google.com ~all"
        return Arrays.stream(spfRecord.split("\\s+"))
            .filter(token -> token.startsWith("ip4:") || token.startsWith("ip6:"))
            .map(token -> token.substring(4)) // Remove "ip4:" or "ip6:" prefix
            .collect(ImmutableSet.toImmutableSet());
    }

    @VisibleForTesting
    boolean isIpAuthorized(String requiredIp, Set<String> spfIps) {
        // Check if the required IP is present in the SPF record
        // Supports both single IPs (192.0.2.10) and CIDR ranges (192.0.2.0/24)

        for (String spfIp : spfIps) {
            if (spfIp.equals(requiredIp)) {
                return true;
            }

            // Check if requiredIp is within a CIDR range in SPF
            if (spfIp.contains("/") && isIpInCidrRange(requiredIp, spfIp)) {
                return true;
            }

            // Check if requiredIp is a CIDR range and spfIp is within it
            if (requiredIp.contains("/") && isIpInCidrRange(spfIp, requiredIp)) {
                return true;
            }
        }

        return false;
    }

    @VisibleForTesting
    boolean isIpInCidrRange(String ip, String cidrRange) {
        try {
            // Simple CIDR validation - can be enhanced with a proper CIDR library if needed
            if (!cidrRange.contains("/")) {
                return false;
            }

            String[] parts = cidrRange.split("/");
            if (parts.length != 2) {
                return false;
            }

            String rangeIp = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            // Remove CIDR notation from IP if present
            String cleanIp = ip.contains("/") ? ip.split("/")[0] : ip;

            InetAddress ipAddress = InetAddress.getByName(cleanIp);
            InetAddress rangeAddress = InetAddress.getByName(rangeIp);

            byte[] ipBytes = ipAddress.getAddress();
            byte[] rangeBytes = rangeAddress.getAddress();

            if (ipBytes.length != rangeBytes.length) {
                return false; // IPv4 vs IPv6 mismatch
            }

            // Check if IP is in CIDR range by comparing prefix bits
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            // Compare full bytes
            for (int i = 0; i < fullBytes; i++) {
                if (ipBytes[i] != rangeBytes[i]) {
                    return false;
                }
            }

            // Compare remaining bits if any
            if (remainingBits > 0 && fullBytes < ipBytes.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((ipBytes[fullBytes] & mask) != (rangeBytes[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;

        } catch (UnknownHostException | NumberFormatException e) {
            LOGGER.debug("Failed to parse IP or CIDR range: {} in {}", ip, cidrRange, e);
            return false;
        }
    }
}
