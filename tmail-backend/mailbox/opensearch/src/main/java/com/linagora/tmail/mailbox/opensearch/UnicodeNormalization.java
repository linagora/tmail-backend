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

package com.linagora.tmail.mailbox.opensearch;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Optional;

import org.apache.james.mailbox.model.SearchQuery;

/**
 * Text can be written composed (NFC: "é" as U+00E9) or decomposed (NFD: "e" followed by U+0301).
 * Both read the same, yet OpenSearch analyzers see different tokens.
 *
 * Normalizing to NFC both the indexed documents and the searched values makes them match whatever the form
 * they were written in.
 */
public final class UnicodeNormalization {
    private UnicodeNormalization() {
    }

    public static String nfc(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    /**
     * Conjunctions are not traversed: their children go through {@link TmailCriterionConverter#convertCriterion}.
     */
    public static SearchQuery.Criterion nfc(SearchQuery.Criterion criterion) {
        return switch (criterion) {
            case SearchQuery.TextCriterion textCriterion -> nfc(textCriterion);
            case SearchQuery.SubjectCriterion subjectCriterion -> new SearchQuery.SubjectCriterion(nfc(subjectCriterion.getSubject()));
            case SearchQuery.HeaderCriterion headerCriterion -> nfc(headerCriterion);
            default -> criterion;
        };
    }

    private static SearchQuery.Criterion nfc(SearchQuery.TextCriterion textCriterion) {
        String value = nfc(textCriterion.getOperator().getValue());
        return switch (textCriterion.getType()) {
            case BODY -> SearchQuery.bodyContains(value);
            case FULL -> SearchQuery.mailContains(value);
            case ATTACHMENTS -> SearchQuery.attachmentContains(value);
            case ATTACHMENT_FILE_NAME -> SearchQuery.attachmentFileName(value);
        };
    }

    private static SearchQuery.Criterion nfc(SearchQuery.HeaderCriterion headerCriterion) {
        return switch (headerCriterion.getOperator()) {
            case SearchQuery.ContainsOperator containsOperator ->
                SearchQuery.headerContains(headerCriterion.getHeaderName(), nfc(containsOperator.getValue()));
            case SearchQuery.AddressOperator addressOperator -> addressType(headerCriterion.getHeaderName())
                .map(addressType -> SearchQuery.address(addressType, nfc(addressOperator.getAddress())))
                .orElse(headerCriterion);
            default -> headerCriterion;
        };
    }

    private static Optional<SearchQuery.AddressType> addressType(String headerName) {
        return Arrays.stream(SearchQuery.AddressType.values())
            .filter(addressType -> addressType.name().equalsIgnoreCase(headerName))
            .findFirst();
    }
}
