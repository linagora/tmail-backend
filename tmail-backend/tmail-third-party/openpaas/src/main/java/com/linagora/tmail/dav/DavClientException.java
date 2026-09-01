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

package com.linagora.tmail.dav;

import java.util.Optional;

import org.apache.james.util.MDCBuilder;

public class DavClientException extends RuntimeException {
    public static final String SABRE_RESPONSE_MDC_KEY = "sabreResponse";
    public static final int SABRE_RESPONSE_MAX_CHARS = 1024;

    /**
     * Builds the MDC context to be used when logging the given error: when it is a {@link DavClientException}
     * carrying the Dav server response, that response is exposed under the {@value #SABRE_RESPONSE_MDC_KEY} key.
     */
    public static MDCBuilder mdcOf(Throwable throwable) {
        if (throwable instanceof DavClientException davClientException) {
            return davClientException.mdc();
        }
        return MDCBuilder.create();
    }

    /**
     * Truncates by Unicode code points rather than by bytes so that no multi-byte UTF-8 sequence
     * nor UTF-16 surrogate pair gets split, which would yield an invalid string.
     */
    public static String truncateSabreResponse(String responseBody) {
        return responseBody.codePoints()
            .limit(SABRE_RESPONSE_MAX_CHARS)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
    }

    private final Optional<String> sabreResponse;

    public DavClientException(String message) {
        super(message);
        this.sabreResponse = Optional.empty();
    }

    public DavClientException(String message, Throwable cause) {
        super(message, cause);
        this.sabreResponse = Optional.empty();
    }

    public DavClientException(String message, String sabreResponse) {
        super(message);
        this.sabreResponse = Optional.of(sabreResponse);
    }

    /**
     * Body of the Dav server response that caused this exception, truncated to the first
     * {@value #SABRE_RESPONSE_MAX_CHARS} characters, when available.
     */
    public Optional<String> sabreResponse() {
        return sabreResponse;
    }

    public MDCBuilder mdc() {
        return MDCBuilder.create()
            .addToContextIfPresent(SABRE_RESPONSE_MDC_KEY, sabreResponse);
    }

    public static class PermissionDenied extends DavClientException {
        public PermissionDenied(String message) {
            super(message);
        }
    }
}
